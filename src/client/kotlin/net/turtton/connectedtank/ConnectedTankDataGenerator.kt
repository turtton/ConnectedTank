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
                generateItemModel(generator, block, tierId)
            }
        }

        override fun generateItemModels(generator: ItemModelGenerator) {}

        private fun createBaseBoxElement(): JsonObject = JsonObject().apply {
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
        }

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
                    JsonArray().apply { add(createBaseBoxElement()) },
                )
            }
            generator.modelCollector.accept(modelId, ModelSupplier { json })
        }

        private fun generateBorderTemplateModels(generator: BlockStateModelGenerator) {
            for ((direction, stripMap) in BORDER_STRIP_ELEMENTS) {
                for ((stripDir, element) in stripMap) {
                    val modelId = Identifier.of(
                        "connectedtank",
                        "block/tank_border_${direction}_$stripDir",
                    )
                    val json = JsonObject().apply {
                        add(
                            "textures",
                            JsonObject().apply {
                                addProperty("particle", "#frame")
                            },
                        )
                        add("elements", JsonArray().apply { add(element) })
                    }
                    generator.modelCollector.accept(modelId, ModelSupplier { json })
                }
            }
        }

        private fun generateBorderChildModels(generator: BlockStateModelGenerator, tierId: String) {
            for ((direction, stripMap) in BORDER_STRIP_ELEMENTS) {
                for (stripDir in stripMap.keys) {
                    val modelId = Identifier.of(
                        "connectedtank",
                        "block/${tierId}_border_${direction}_$stripDir",
                    )
                    val json = JsonObject().apply {
                        addProperty(
                            "parent",
                            "connectedtank:block/tank_border_${direction}_$stripDir",
                        )
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
            // 各ストリップ: border 方向が非接続 AND ストリップ方向も非接続のとき表示
            for ((dirName, stripMap) in BORDER_STRIP_ELEMENTS) {
                val borderProperty = requireNotNull(directionProperties[dirName])
                for (stripDir in stripMap.keys) {
                    val stripProperty = requireNotNull(directionProperties[stripDir])
                    val modelId = Identifier.of(
                        "connectedtank",
                        "block/${tierId}_border_${dirName}_$stripDir",
                    )
                    supplier.with(
                        MultipartModelConditionBuilder()
                            .put(borderProperty, false)
                            .put(stripProperty, false),
                        WeightedVariant(Pool.of(ModelVariant(modelId))),
                    )
                }
            }

            generator.blockStateCollector.accept(supplier)
        }

        // The item model always includes all border overlays for every direction,
        // because a standalone item is never connected to adjacent blocks.
        private fun generateItemModel(generator: BlockStateModelGenerator, block: Block, tierId: String) {
            val modelId = Identifier.of("connectedtank", "block/${tierId}_item")
            val json = JsonObject().apply {
                addProperty("parent", "minecraft:block/block")
                add(
                    "textures",
                    JsonObject().apply {
                        addProperty("side", "connectedtank:block/${tierId}_side")
                        addProperty("frame", "connectedtank:block/${tierId}_frame")
                        addProperty("particle", "connectedtank:block/${tierId}_frame")
                    },
                )
                add(
                    "elements",
                    JsonArray().apply {
                        add(createBaseBoxElement())
                        for ((_, stripMap) in BORDER_STRIP_ELEMENTS) {
                            for ((_, element) in stripMap) {
                                add(element)
                            }
                        }
                    },
                )
            }
            generator.modelCollector.accept(modelId, ModelSupplier { json })
            generator.registerParentedItemModel(block, modelId)
        }

        private fun jsonArray(vararg values: Number): JsonArray = JsonArray().apply {
            values.forEach { add(it) }
        }

        private val OPPOSITE_FACE: Map<String, String> = mapOf(
            "north" to "south",
            "south" to "north",
            "east" to "west",
            "west" to "east",
            "up" to "down",
            "down" to "up",
        )

        // cullface を付けないこと。isSideInvisible() が Direction 単位で判定するため、
        // partial face の border strip まで巻き添えで cull される。
        // 各ストリップは表面 (face) と裏面 (OPPOSITE_FACE[face]) の両面を持つ。
        // Minecraft の model quad は背面カリングされるため、透明ブロックを通して
        // 裏側のボーダーを見えるようにするために反対面が必要。
        // 各ストリップは個別モデルに分離し、multipart の AND 条件
        // (connected_{borderDir}=false AND connected_{stripDir}=false) で制御する。
        private fun borderStripElement(
            from: Triple<Number, Number, Number>,
            to: Triple<Number, Number, Number>,
            face: String,
        ): JsonObject = JsonObject().apply {
            add("from", jsonArray(from.first, from.second, from.third))
            add("to", jsonArray(to.first, to.second, to.third))
            val frameFace = JsonObject().apply {
                addProperty("texture", "#frame")
            }
            add(
                "faces",
                JsonObject().apply {
                    add(face, frameFace)
                    add(requireNotNull(OPPOSITE_FACE[face]) { "Unknown face: $face" }, frameFace)
                },
            )
        }

        // ボーダーストリップ要素。各ストリップは個別モデルに分離し、
        // multipart の AND 条件で制御する。
        // キー: border 方向 → ストリップの face 方向 → 両面要素
        @Suppress("LongMethod")
        private val BORDER_STRIP_ELEMENTS: Map<String, Map<String, JsonObject>> by lazy {
            mapOf(
                "up" to mapOf(
                    "north" to borderStripElement(Triple(0, 15, -0.01), Triple(16, 16, 0.01), "north"),
                    "south" to borderStripElement(Triple(0, 15, 15.99), Triple(16, 16, 16.01), "south"),
                    "east" to borderStripElement(Triple(15.99, 15, 0), Triple(16.01, 16, 16), "east"),
                    "west" to borderStripElement(Triple(-0.01, 15, 0), Triple(0.01, 16, 16), "west"),
                ),
                "down" to mapOf(
                    "north" to borderStripElement(Triple(0, 0, -0.01), Triple(16, 1, 0.01), "north"),
                    "south" to borderStripElement(Triple(0, 0, 15.99), Triple(16, 1, 16.01), "south"),
                    "east" to borderStripElement(Triple(15.99, 0, 0), Triple(16.01, 1, 16), "east"),
                    "west" to borderStripElement(Triple(-0.01, 0, 0), Triple(0.01, 1, 16), "west"),
                ),
                "north" to mapOf(
                    "east" to borderStripElement(Triple(15.99, 0, 0), Triple(16.01, 16, 1), "east"),
                    "west" to borderStripElement(Triple(-0.01, 0, 0), Triple(0.01, 16, 1), "west"),
                    "up" to borderStripElement(Triple(0, 15.99, 0), Triple(16, 16.01, 1), "up"),
                    "down" to borderStripElement(Triple(0, -0.01, 0), Triple(16, 0.01, 1), "down"),
                ),
                "south" to mapOf(
                    "east" to borderStripElement(Triple(15.99, 0, 15), Triple(16.01, 16, 16), "east"),
                    "west" to borderStripElement(Triple(-0.01, 0, 15), Triple(0.01, 16, 16), "west"),
                    "up" to borderStripElement(Triple(0, 15.99, 15), Triple(16, 16.01, 16), "up"),
                    "down" to borderStripElement(Triple(0, -0.01, 15), Triple(16, 0.01, 16), "down"),
                ),
                "east" to mapOf(
                    "north" to borderStripElement(Triple(15, 0, -0.01), Triple(16, 16, 0.01), "north"),
                    "south" to borderStripElement(Triple(15, 0, 15.99), Triple(16, 16, 16.01), "south"),
                    "up" to borderStripElement(Triple(15, 15.99, 0), Triple(16, 16.01, 16), "up"),
                    "down" to borderStripElement(Triple(15, -0.01, 0), Triple(16, 0.01, 16), "down"),
                ),
                "west" to mapOf(
                    "north" to borderStripElement(Triple(0, 0, -0.01), Triple(1, 16, 0.01), "north"),
                    "south" to borderStripElement(Triple(0, 0, 15.99), Triple(1, 16, 16.01), "south"),
                    "up" to borderStripElement(Triple(0, 15.99, 0), Triple(1, 16.01, 16), "up"),
                    "down" to borderStripElement(Triple(0, -0.01, 0), Triple(1, 0.01, 16), "down"),
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
