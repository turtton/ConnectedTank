package net.turtton.connectedtank

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
import net.minecraft.advancement.AdvancementRequirements
import net.minecraft.advancement.AdvancementRewards
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion
import net.minecraft.block.Block
import net.minecraft.client.data.BlockStateModelGenerator
import net.minecraft.client.data.ItemModelGenerator
import net.minecraft.client.data.ModelSupplier
import net.minecraft.client.data.MultipartBlockModelDefinitionCreator
import net.minecraft.client.render.model.json.ModelVariant
import net.minecraft.client.render.model.json.MultipartModelConditionBuilder
import net.minecraft.client.render.model.json.WeightedVariant
import net.minecraft.data.recipe.RecipeExporter
import net.minecraft.data.recipe.RecipeGenerator
import net.minecraft.data.recipe.SmithingTransformRecipeJsonBuilder
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.recipe.Ingredient
import net.minecraft.recipe.RawShapedRecipe
import net.minecraft.recipe.ShapedRecipe
import net.minecraft.recipe.book.CraftingRecipeCategory
import net.minecraft.recipe.book.RecipeCategory
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.tag.ItemTags
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.collection.Pool
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.extension.ModIdentifier
import net.turtton.connectedtank.recipe.TankUpgradeRecipe

object ConnectedTankDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::ModelProvider)
        pack.addProvider(::RecipeProvider)
        pack.addProvider(::EnglishLanguageProvider)
        pack.addProvider(::JapaneseLanguageProvider)
    }

    private class ModelProvider(output: FabricDataOutput) : FabricModelProvider(output) {
        override fun generateBlockStateModels(generator: BlockStateModelGenerator) {
            generateBorderTemplateModels(generator)

            for (block in CTBlocks.ALL_TANKS) {
                val tankBlock = block as ConnectedTankBlock
                val tierId = tankBlock.tier.id

                generateBaseModel(generator, tierId)
                generateBorderChildModels(generator, tierId)
                generateMultipartBlockState(generator, block, tierId)
                generateItemModel(generator, tierId)
            }
        }

        override fun generateItemModels(generator: ItemModelGenerator) {}

        private fun generateBaseModel(generator: BlockStateModelGenerator, tierId: String) {
            val modelId = Identifier.of("connectedtank", "block/$tierId")
            val json = JsonObject().apply {
                add(
                    "textures",
                    JsonObject().apply {
                        addProperty("side", "connectedtank:block/${tierId}_side")
                        addProperty("particle", "connectedtank:block/${tierId}_frame")
                    },
                )
                add(
                    "elements",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                add("from", jsonArray(0, 0, 0))
                                add("to", jsonArray(16, 16, 16))
                                add(
                                    "faces",
                                    JsonObject().apply {
                                        for (dir in listOf("north", "south", "east", "west")) {
                                            add(
                                                dir,
                                                JsonObject().apply {
                                                    addProperty("texture", "#side")
                                                    addProperty("cullface", dir)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            }
            generator.modelCollector.accept(modelId, ModelSupplier { json })
        }

        private fun generateBorderTemplateModels(generator: BlockStateModelGenerator) {
            for ((direction, elements) in BORDER_OVERLAY_ELEMENTS) {
                val modelId = Identifier.of("connectedtank", "block/tank_border_$direction")
                val json = JsonObject().apply {
                    add(
                        "textures",
                        JsonObject().apply {
                            addProperty("particle", "#frame")
                        },
                    )
                    add(
                        "elements",
                        JsonArray().apply {
                            for (element in elements) {
                                add(element)
                            }
                        },
                    )
                }
                generator.modelCollector.accept(modelId, ModelSupplier { json })
            }
        }

        private fun generateBorderChildModels(generator: BlockStateModelGenerator, tierId: String) {
            for (direction in BORDER_OVERLAY_ELEMENTS.keys) {
                val modelId = Identifier.of("connectedtank", "block/${tierId}_border_$direction")
                val json = JsonObject().apply {
                    addProperty("parent", "connectedtank:block/tank_border_$direction")
                    add(
                        "textures",
                        JsonObject().apply {
                            addProperty("frame", "connectedtank:block/${tierId}_frame")
                        },
                    )
                }
                generator.modelCollector.accept(modelId, ModelSupplier { json })
            }
        }

        private fun generateMultipartBlockState(
            generator: BlockStateModelGenerator,
            block: Block,
            tierId: String,
        ) {
            val supplier = MultipartBlockModelDefinitionCreator.create(block)

            // Base model (always applied)
            val baseModelId = Identifier.of("connectedtank", "block/$tierId")
            supplier.with(WeightedVariant(Pool.of(ModelVariant(baseModelId))))

            // Border overlays (applied when NOT connected in each direction)
            val directionProperties = mapOf(
                "up" to ConnectedTankBlock.CONNECTED_UP,
                "down" to ConnectedTankBlock.CONNECTED_DOWN,
                "north" to ConnectedTankBlock.CONNECTED_NORTH,
                "south" to ConnectedTankBlock.CONNECTED_SOUTH,
                "east" to ConnectedTankBlock.CONNECTED_EAST,
                "west" to ConnectedTankBlock.CONNECTED_WEST,
            )
            for ((dirName, property) in directionProperties) {
                val borderModelId = Identifier.of("connectedtank", "block/${tierId}_border_$dirName")
                supplier.with(
                    MultipartModelConditionBuilder().put(property, false),
                    WeightedVariant(Pool.of(ModelVariant(borderModelId))),
                )
            }

            generator.blockStateCollector.accept(supplier)
        }

        private fun generateItemModel(generator: BlockStateModelGenerator, tierId: String) {
            val modelId = Identifier.of("connectedtank", "item/$tierId")
            val json = JsonObject().apply {
                addProperty("parent", "minecraft:item/generated")
                add(
                    "textures",
                    JsonObject().apply {
                        addProperty("layer0", "connectedtank:block/${tierId}_item")
                    },
                )
            }
            generator.modelCollector.accept(modelId, ModelSupplier { json })
        }

        private fun jsonArray(vararg values: Number): JsonArray = JsonArray().apply {
            values.forEach { add(it) }
        }

        private fun borderElement(
            from: Triple<Number, Number, Number>,
            to: Triple<Number, Number, Number>,
            face: String,
            cullface: String,
        ): JsonObject = JsonObject().apply {
            add("from", jsonArray(from.first, from.second, from.third))
            add("to", jsonArray(to.first, to.second, to.third))
            add(
                "faces",
                JsonObject().apply {
                    add(
                        face,
                        JsonObject().apply {
                            addProperty("texture", "#frame")
                            addProperty("cullface", cullface)
                        },
                    )
                },
            )
        }

        @Suppress("LongMethod")
        private val BORDER_OVERLAY_ELEMENTS: Map<String, List<JsonObject>> by lazy {
            mapOf(
                "up" to listOf(
                    borderElement(Triple(0, 15, -0.01), Triple(16, 16, 0.01), "north", "north"),
                    borderElement(Triple(0, 15, 15.99), Triple(16, 16, 16.01), "south", "south"),
                    borderElement(Triple(15.99, 15, 0), Triple(16.01, 16, 16), "east", "east"),
                    borderElement(Triple(-0.01, 15, 0), Triple(0.01, 16, 16), "west", "west"),
                ),
                "down" to listOf(
                    borderElement(Triple(0, 0, -0.01), Triple(16, 1, 0.01), "north", "north"),
                    borderElement(Triple(0, 0, 15.99), Triple(16, 1, 16.01), "south", "south"),
                    borderElement(Triple(15.99, 0, 0), Triple(16.01, 1, 16), "east", "east"),
                    borderElement(Triple(-0.01, 0, 0), Triple(0.01, 1, 16), "west", "west"),
                ),
                "north" to listOf(
                    borderElement(Triple(15.99, 0, 0), Triple(16.01, 16, 1), "east", "east"),
                    borderElement(Triple(-0.01, 0, 0), Triple(0.01, 16, 1), "west", "west"),
                    borderElement(Triple(0, 15.99, 0), Triple(16, 16.01, 1), "up", "up"),
                    borderElement(Triple(0, -0.01, 0), Triple(16, 0.01, 1), "down", "down"),
                ),
                "south" to listOf(
                    borderElement(Triple(15.99, 0, 15), Triple(16.01, 16, 16), "east", "east"),
                    borderElement(Triple(-0.01, 0, 15), Triple(0.01, 16, 16), "west", "west"),
                    borderElement(Triple(0, 15.99, 15), Triple(16, 16.01, 16), "up", "up"),
                    borderElement(Triple(0, -0.01, 15), Triple(16, 0.01, 16), "down", "down"),
                ),
                "east" to listOf(
                    borderElement(Triple(15, 0, -0.01), Triple(16, 16, 0.01), "north", "north"),
                    borderElement(Triple(15, 0, 15.99), Triple(16, 16, 16.01), "south", "south"),
                    borderElement(Triple(15, 15.99, 0), Triple(16, 16.01, 16), "up", "up"),
                    borderElement(Triple(15, -0.01, 0), Triple(16, 0.01, 16), "down", "down"),
                ),
                "west" to listOf(
                    borderElement(Triple(0, 0, -0.01), Triple(1, 16, 0.01), "north", "north"),
                    borderElement(Triple(0, 0, 15.99), Triple(1, 16, 16.01), "south", "south"),
                    borderElement(Triple(0, 15.99, 0), Triple(1, 16.01, 16), "up", "up"),
                    borderElement(Triple(0, -0.01, 0), Triple(1, 0.01, 16), "down", "down"),
                ),
            )
        }
    }

    private class RecipeProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<RegistryWrapper.WrapperLookup>,
    ) : FabricRecipeProvider(output, registriesFuture) {
        override fun getName(): String = "ConnectedTank Recipes"

        override fun getRecipeGenerator(
            registryLookup: RegistryWrapper.WrapperLookup,
            exporter: RecipeExporter,
        ): RecipeGenerator = object : RecipeGenerator(registryLookup, exporter) {
            override fun generate() {
                // Base tank recipe (normal shaped)
                createShaped(RecipeCategory.DECORATIONS, CTBlocks.CONNECTED_TANK)
                    .pattern("PGP")
                    .pattern("GPG")
                    .pattern("PGP")
                    .input('P', ItemTags.PLANKS)
                    .input('G', Items.GLASS)
                    .criterion("has_planks", conditionsFromTag(ItemTags.PLANKS))
                    .criterion(hasItem(Items.GLASS), conditionsFromItem(Items.GLASS))
                    .offerTo(exporter)

                // Upgrade recipes
                offerTankUpgrade(
                    CTBlocks.CONNECTED_TANK,
                    CTBlocks.STONE_CONNECTED_TANK,
                    ItemTags.STONE_CRAFTING_MATERIALS,
                    "has_stone",
                    conditionsFromTag(ItemTags.STONE_CRAFTING_MATERIALS),
                )
                offerTankUpgradeItem(
                    CTBlocks.STONE_CONNECTED_TANK,
                    CTBlocks.COPPER_CONNECTED_TANK,
                    Items.COPPER_INGOT,
                )
                offerTankUpgradeItem(
                    CTBlocks.COPPER_CONNECTED_TANK,
                    CTBlocks.IRON_CONNECTED_TANK,
                    Items.IRON_INGOT,
                )
                offerTankUpgradeItem(
                    CTBlocks.IRON_CONNECTED_TANK,
                    CTBlocks.GOLD_CONNECTED_TANK,
                    Items.GOLD_INGOT,
                )
                offerTankUpgradeItem(
                    CTBlocks.GOLD_CONNECTED_TANK,
                    CTBlocks.DIAMOND_CONNECTED_TANK,
                    Items.DIAMOND,
                )
                // Netherite upgrade uses smithing table
                val netheriteRecipeKey = RegistryKey.of(
                    RegistryKeys.RECIPE,
                    ModIdentifier(
                        (CTBlocks.NETHERITE_CONNECTED_TANK as net.turtton.connectedtank.block.ConnectedTankBlock).tier.id,
                    ),
                )
                SmithingTransformRecipeJsonBuilder.create(
                    Ingredient.ofItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.ofItem(CTBlocks.DIAMOND_CONNECTED_TANK),
                    ingredientFromTag(ItemTags.NETHERITE_TOOL_MATERIALS),
                    RecipeCategory.DECORATIONS,
                    CTBlocks.NETHERITE_CONNECTED_TANK.asItem(),
                )
                    .criterion("has_netherite_ingot", conditionsFromTag(ItemTags.NETHERITE_TOOL_MATERIALS))
                    .offerTo(exporter, netheriteRecipeKey)
            }

            private fun offerTankUpgradeItem(
                input: Block,
                output: Block,
                material: Item,
            ) {
                val materialIngredient = net.minecraft.recipe.Ingredient.ofItem(material)
                val inputIngredient = net.minecraft.recipe.Ingredient.ofItem(input)
                val raw = RawShapedRecipe.create(
                    mapOf('M' to materialIngredient, 'T' to inputIngredient),
                    "MMM",
                    "MTM",
                    "MMM",
                )
                val recipeKey = RegistryKey.of(RegistryKeys.RECIPE, ModIdentifier((output as net.turtton.connectedtank.block.ConnectedTankBlock).tier.id))
                val shaped = ShapedRecipe(
                    "",
                    CraftingRecipeCategory.MISC,
                    raw,
                    ItemStack(output),
                )
                val recipe = TankUpgradeRecipe(shaped)
                val advancement = exporter.advancementBuilder
                    .criterion("has_the_recipe", RecipeUnlockedCriterion.create(recipeKey))
                    .rewards(AdvancementRewards.Builder.recipe(recipeKey))
                    .criteriaMerger(AdvancementRequirements.CriterionMerger.OR)
                    .criterion(hasItem(material), conditionsFromItem(material))
                exporter.accept(
                    recipeKey,
                    recipe,
                    advancement.build(recipeKey.value.withPrefixedPath("recipes/${RecipeCategory.DECORATIONS.name.lowercase()}/")),
                )
            }

            private fun offerTankUpgrade(
                input: Block,
                output: Block,
                materialTag: TagKey<Item>,
                criterionName: String,
                criterion: net.minecraft.advancement.AdvancementCriterion<*>,
            ) {
                val materialIngredient = ingredientFromTag(materialTag)
                val inputIngredient = net.minecraft.recipe.Ingredient.ofItem(input)
                val raw = RawShapedRecipe.create(
                    mapOf('M' to materialIngredient, 'T' to inputIngredient),
                    "MMM",
                    "MTM",
                    "MMM",
                )
                val recipeKey = RegistryKey.of(RegistryKeys.RECIPE, ModIdentifier((output as net.turtton.connectedtank.block.ConnectedTankBlock).tier.id))
                val shaped = ShapedRecipe(
                    "",
                    CraftingRecipeCategory.MISC,
                    raw,
                    ItemStack(output),
                )
                val recipe = TankUpgradeRecipe(shaped)
                val advancement = exporter.advancementBuilder
                    .criterion("has_the_recipe", RecipeUnlockedCriterion.create(recipeKey))
                    .rewards(AdvancementRewards.Builder.recipe(recipeKey))
                    .criteriaMerger(AdvancementRequirements.CriterionMerger.OR)
                    .criterion(criterionName, criterion)
                exporter.accept(
                    recipeKey,
                    recipe,
                    advancement.build(recipeKey.value.withPrefixedPath("recipes/${RecipeCategory.DECORATIONS.name.lowercase()}/")),
                )
            }
        }
    }

    private class EnglishLanguageProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<RegistryWrapper.WrapperLookup>,
    ) : FabricLanguageProvider(output, registriesFuture) {
        override fun generateTranslations(
            registryLookup: RegistryWrapper.WrapperLookup,
            builder: TranslationBuilder,
        ) {
            builder.add(CTBlocks.CONNECTED_TANK, "Tank")
            builder.add(CTBlocks.STONE_CONNECTED_TANK, "Stone Tank")
            builder.add(CTBlocks.COPPER_CONNECTED_TANK, "Copper Tank")
            builder.add(CTBlocks.IRON_CONNECTED_TANK, "Iron Tank")
            builder.add(CTBlocks.GOLD_CONNECTED_TANK, "Golden Tank")
            builder.add(CTBlocks.DIAMOND_CONNECTED_TANK, "Diamond Tank")
            builder.add(CTBlocks.NETHERITE_CONNECTED_TANK, "Netherite Tank")
            builder.add("itemGroup.connectedtank.item_group", "Connected Tank")
            builder.add("config.connectedtank.title", "ConnectedTank Config")
            builder.add("config.connectedtank.category.server", "Server")
            builder.add("config.connectedtank.category.client", "Client")
            builder.add("config.connectedtank.server.tankBucketCapacity", "Tank Bucket Capacity")
            builder.add(
                "config.connectedtank.server.tankBucketCapacity.description",
                "Bucket capacity per single tank block",
            )
            builder.add("config.connectedtank.client.renderQuality", "Render Quality")
            builder.add(
                "config.connectedtank.client.renderQuality.description",
                "Rendering quality for tank fluid display",
            )
        }
    }

    private class JapaneseLanguageProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<RegistryWrapper.WrapperLookup>,
    ) : FabricLanguageProvider(output, "ja_jp", registriesFuture) {
        override fun generateTranslations(
            registryLookup: RegistryWrapper.WrapperLookup,
            builder: TranslationBuilder,
        ) {
            builder.add(CTBlocks.CONNECTED_TANK, "タンク")
            builder.add(CTBlocks.STONE_CONNECTED_TANK, "石のタンク")
            builder.add(CTBlocks.COPPER_CONNECTED_TANK, "銅のタンク")
            builder.add(CTBlocks.IRON_CONNECTED_TANK, "鉄のタンク")
            builder.add(CTBlocks.GOLD_CONNECTED_TANK, "金のタンク")
            builder.add(CTBlocks.DIAMOND_CONNECTED_TANK, "ダイヤモンドのタンク")
            builder.add(CTBlocks.NETHERITE_CONNECTED_TANK, "ネザライトのタンク")
            builder.add("itemGroup.connectedtank.item_group", "Connected Tank")
            builder.add("config.connectedtank.title", "ConnectedTank 設定")
            builder.add("config.connectedtank.category.server", "サーバー")
            builder.add("config.connectedtank.category.client", "クライアント")
            builder.add("config.connectedtank.server.tankBucketCapacity", "タンクバケツ容量")
            builder.add(
                "config.connectedtank.server.tankBucketCapacity.description",
                "タンク 1 ブロックあたりのバケツ容量",
            )
            builder.add("config.connectedtank.client.renderQuality", "描画品質")
            builder.add(
                "config.connectedtank.client.renderQuality.description",
                "タンク内液体の描画品質",
            )
        }
    }
}
