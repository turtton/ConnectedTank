//? if fabric {
package net.turtton.connectedtank

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
//? if >=26.1 {
/*import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput*/
//?} else {
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
//?}
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
//? if >=26.1 {
/*import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider*/
//?} else {
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
//?}
import net.minecraft.advancements.AdvancementRequirements
import net.minecraft.advancements.AdvancementRewards
//? if >=1.21.11 {
/*import net.minecraft.advancements.criterion.RecipeUnlockedTrigger*/
//?} else {
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger
//?}
import net.minecraft.world.level.block.Block
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.model.ItemModelUtils
import net.minecraft.client.data.models.model.ModelInstance
import net.minecraft.client.data.models.blockstates.MultiPartGenerator
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.dispatch.Variant*/
//?} else {
import net.minecraft.client.renderer.block.model.Variant
//?}
import net.minecraft.client.data.models.blockstates.ConditionBuilder
import net.minecraft.client.data.models.MultiVariant
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder
import net.minecraft.world.item.Item
//? if <26.1 {
import net.minecraft.world.item.ItemStack
//?}
//? if >=26.1 {
/*import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.Recipe*/
//?}
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.core.HolderLookup
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import net.minecraft.util.random.WeightedList
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.extension.ModIdentifier
import net.turtton.connectedtank.item.ConnectedTankItemRenderer
import net.turtton.connectedtank.recipe.TankUpgradeRecipe

object ConnectedTankDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::ModelProvider)
        pack.addProvider(::CTRecipeProvider)
        pack.addProvider(::BlockTagProvider)
        pack.addProvider(::EnglishLanguageProvider)
        pack.addProvider(::JapaneseLanguageProvider)
    }

    //? if >=26.1 {
    /*private class ModelProvider(output: FabricPackOutput) : FabricModelProvider(output) {*/
    //?} else {
    private class ModelProvider(output: FabricDataOutput) : FabricModelProvider(output) {
        //?}
        override fun generateBlockStateModels(generator: BlockModelGenerators) {
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

        override fun generateItemModels(generator: ItemModelGenerators) {}

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

        // borderStripElement と同じ理由で両面を持つ薄パネル（背面カリング対策）。
        // 両面とも同じ方向の cullface を持つ。これにより隣接不透明ブロックによるカリングと
        // isSideInvisible() による接続時カリングの両方で、壁全体が一括で消える。
        private fun createSidePanelElement(
            from: Triple<Number, Number, Number>,
            to: Triple<Number, Number, Number>,
            face: String,
        ): JsonObject = JsonObject().apply {
            add("from", jsonArray(from.first, from.second, from.third))
            add("to", jsonArray(to.first, to.second, to.third))
            val oppositeFace = requireNotNull(OPPOSITE_FACE[face]) { "Unknown face: $face" }
            add(
                "faces",
                JsonObject().apply {
                    add(
                        face,
                        JsonObject().apply {
                            addProperty("texture", "#side")
                            addProperty("cullface", face)
                        },
                    )
                    add(
                        oppositeFace,
                        JsonObject().apply {
                            addProperty("texture", "#side")
                            // 裏面から見た時にテクスチャが左右反転して見えるよう U 軸を反転
                            add("uv", jsonArray(16, 0, 0, 16))
                            addProperty("cullface", face)
                        },
                    )
                },
            )
        }

        private val SIDE_PANEL_ELEMENTS: List<JsonObject> = listOf(
            createSidePanelElement(Triple(0, 0, 0), Triple(0.01, 16, 16), "west"),
            createSidePanelElement(Triple(15.99, 0, 0), Triple(16, 16, 16), "east"),
            createSidePanelElement(Triple(0, 0, 0), Triple(16, 16, 0.01), "north"),
            createSidePanelElement(Triple(0, 0, 15.99), Triple(16, 16, 16), "south"),
        )

        private fun generateBaseModel(generator: BlockModelGenerators, tierId: String) {
            val modelId = ResourceLocation.fromNamespaceAndPath("connectedtank", "block/$tierId")
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
                    JsonArray().apply { SIDE_PANEL_ELEMENTS.forEach { add(it) } },
                )
            }
            generator.modelOutput.accept(modelId, ModelInstance { json })
        }

        private fun generateBorderTemplateModels(generator: BlockModelGenerators) {
            for ((direction, stripMap) in BORDER_STRIP_ELEMENTS) {
                for ((stripDir, element) in stripMap) {
                    val modelId = ResourceLocation.fromNamespaceAndPath(
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
                    generator.modelOutput.accept(modelId, ModelInstance { json })
                }
            }
        }

        private fun generateBorderChildModels(generator: BlockModelGenerators, tierId: String) {
            for ((direction, stripMap) in BORDER_STRIP_ELEMENTS) {
                for (stripDir in stripMap.keys) {
                    val modelId = ResourceLocation.fromNamespaceAndPath(
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
                    generator.modelOutput.accept(modelId, ModelInstance { json })
                }
            }
        }

        private fun generateMultipartBlockState(
            generator: BlockModelGenerators,
            block: Block,
            tierId: String,
        ) {
            val supplier = MultiPartGenerator.multiPart(block)

            // Base model (always applied)
            val baseModelId = ResourceLocation.fromNamespaceAndPath("connectedtank", "block/$tierId")
            supplier.with(MultiVariant(WeightedList.of(Variant(baseModelId))))

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
                    val modelId = ResourceLocation.fromNamespaceAndPath(
                        "connectedtank",
                        "block/${tierId}_border_${dirName}_$stripDir",
                    )
                    supplier.with(
                        ConditionBuilder()
                            .term(borderProperty, false)
                            .term(stripProperty, false),
                        MultiVariant(WeightedList.of(Variant(modelId))),
                    )
                }
            }

            generator.blockStateOutput.accept(supplier)
        }

        // The item model always includes all border overlays for every direction,
        // because a standalone item is never connected to adjacent blocks.
        private fun generateItemModel(generator: BlockModelGenerators, block: Block, tierId: String) {
            val modelId = ResourceLocation.fromNamespaceAndPath("connectedtank", "block/${tierId}_item")
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
                        SIDE_PANEL_ELEMENTS.forEach { add(it) }
                        for ((_, stripMap) in BORDER_STRIP_ELEMENTS) {
                            for ((_, element) in stripMap) {
                                add(element)
                            }
                        }
                    },
                )
            }
            generator.modelOutput.accept(modelId, ModelInstance { json })
            generator.itemModelOutput.accept(
                block.asItem(),
                ItemModelUtils.composite(
                    ItemModelUtils.plainModel(modelId),
                    ItemModelUtils.specialModel(modelId, ConnectedTankItemRenderer.Unbaked()),
                ),
            )
        }

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

    //? if >=26.1 {
    /*private class BlockTagProvider(
        output: FabricPackOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricTagsProvider.BlockTagsProvider(output, registriesFuture) {*/
    //?} else {
    private class BlockTagProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricTagProvider.BlockTagProvider(output, registriesFuture) {
        //?}
        override fun addTags(wrapperLookup: HolderLookup.Provider) {
            val pickaxeMineable = valueLookupBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
            for (block in CTBlocks.ALL_TANKS) {
                pickaxeMineable.add(block)
            }
        }
    }

    //? if >=26.1 {
    /*private class CTRecipeProvider(
        output: FabricPackOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricRecipeProvider(output, registriesFuture) {*/
    //?} else {
    private class CTRecipeProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricRecipeProvider(output, registriesFuture) {
        //?}
        override fun getName(): String = "ConnectedTank Recipes"

        override fun createRecipeProvider(
            registryLookup: HolderLookup.Provider,
            exporter: RecipeOutput,
        ): net.minecraft.data.recipes.RecipeProvider = object : net.minecraft.data.recipes.RecipeProvider(registryLookup, exporter) {
            override fun buildRecipes() {
                // Base tank recipe (normal shaped)
                shaped(RecipeCategory.DECORATIONS, CTBlocks.CONNECTED_TANK)
                    .pattern("PGP")
                    .pattern("GPG")
                    .pattern("PGP")
                    .define('P', ItemTags.PLANKS)
                    .define('G', Items.GLASS)
                    .unlockedBy("has_planks", has(ItemTags.PLANKS))
                    .unlockedBy(getHasName(Items.GLASS), has(Items.GLASS))
                    .save(exporter)

                // Upgrade recipes
                offerTankUpgrade(
                    CTBlocks.CONNECTED_TANK,
                    CTBlocks.STONE_CONNECTED_TANK,
                    ItemTags.STONE_CRAFTING_MATERIALS,
                    "has_stone",
                    has(ItemTags.STONE_CRAFTING_MATERIALS),
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
                val netheriteRecipeKey = ResourceKey.create(
                    Registries.RECIPE,
                    ModIdentifier(
                        (CTBlocks.NETHERITE_CONNECTED_TANK as net.turtton.connectedtank.block.ConnectedTankBlock).tier.id,
                    ),
                )
                SmithingTransformRecipeBuilder.smithing(
                    Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(CTBlocks.DIAMOND_CONNECTED_TANK),
                    tag(ItemTags.NETHERITE_TOOL_MATERIALS),
                    RecipeCategory.DECORATIONS,
                    CTBlocks.NETHERITE_CONNECTED_TANK.asItem(),
                )
                    .unlocks("has_netherite_ingot", has(ItemTags.NETHERITE_TOOL_MATERIALS))
                    .save(exporter, netheriteRecipeKey)
            }

            private fun offerTankUpgradeItem(
                input: Block,
                output: Block,
                material: Item,
            ) {
                val materialIngredient = net.minecraft.world.item.crafting.Ingredient.of(material)
                val inputIngredient = net.minecraft.world.item.crafting.Ingredient.of(input)
                val raw = ShapedRecipePattern.of(
                    mapOf('M' to materialIngredient, 'T' to inputIngredient),
                    "MMM",
                    "MTM",
                    "MMM",
                )
                val recipeKey = ResourceKey.create(Registries.RECIPE, ModIdentifier((output as net.turtton.connectedtank.block.ConnectedTankBlock).tier.id))
                //? if >=26.1 {
                /*val shaped = ShapedRecipe(
                    Recipe.CommonInfo(true),
                    CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                    raw,
                    ItemStackTemplate(output.asItem()),
                )*/
                //?} else {
                val shaped = ShapedRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    raw,
                    ItemStack(output),
                )
                //?}
                val recipe = TankUpgradeRecipe(shaped)
                val advancement = exporter.advancement()
                    .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeKey))
                    .rewards(AdvancementRewards.Builder.recipe(recipeKey))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .addCriterion(getHasName(material), has(material))
                exporter.accept(
                    recipeKey,
                    recipe,
                    //? if >=1.21.11 {
                    /*advancement.build(recipeKey.identifier().withPrefix("recipes/${RecipeCategory.DECORATIONS.name.lowercase()}/")),*/
                    //?} else {
                    advancement.build(recipeKey.location().withPrefix("recipes/${RecipeCategory.DECORATIONS.name.lowercase()}/")),
                    //?}
                )
            }

            private fun offerTankUpgrade(
                input: Block,
                output: Block,
                materialTag: TagKey<Item>,
                criterionName: String,
                criterion: net.minecraft.advancements.Criterion<*>,
            ) {
                val materialIngredient = tag(materialTag)
                val inputIngredient = net.minecraft.world.item.crafting.Ingredient.of(input)
                val raw = ShapedRecipePattern.of(
                    mapOf('M' to materialIngredient, 'T' to inputIngredient),
                    "MMM",
                    "MTM",
                    "MMM",
                )
                val recipeKey = ResourceKey.create(Registries.RECIPE, ModIdentifier((output as net.turtton.connectedtank.block.ConnectedTankBlock).tier.id))
                //? if >=26.1 {
                /*val shaped = ShapedRecipe(
                    Recipe.CommonInfo(true),
                    CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                    raw,
                    ItemStackTemplate(output.asItem()),
                )*/
                //?} else {
                val shaped = ShapedRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    raw,
                    ItemStack(output),
                )
                //?}
                val recipe = TankUpgradeRecipe(shaped)
                val advancement = exporter.advancement()
                    .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeKey))
                    .rewards(AdvancementRewards.Builder.recipe(recipeKey))
                    .requirements(AdvancementRequirements.Strategy.OR)
                    .addCriterion(criterionName, criterion)
                exporter.accept(
                    recipeKey,
                    recipe,
                    //? if >=1.21.11 {
                    /*advancement.build(recipeKey.identifier().withPrefix("recipes/${RecipeCategory.DECORATIONS.name.lowercase()}/")),*/
                    //?} else {
                    advancement.build(recipeKey.location().withPrefix("recipes/${RecipeCategory.DECORATIONS.name.lowercase()}/")),
                    //?}
                )
            }
        }
    }

    //? if >=26.1 {
    /*private class EnglishLanguageProvider(
        output: FabricPackOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricLanguageProvider(output, registriesFuture) {*/
    //?} else {
    private class EnglishLanguageProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricLanguageProvider(output, registriesFuture) {
        //?}
        override fun generateTranslations(
            registryLookup: HolderLookup.Provider,
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

    //? if >=26.1 {
    /*private class JapaneseLanguageProvider(
        output: FabricPackOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricLanguageProvider(output, "ja_jp", registriesFuture) {*/
    //?} else {
    private class JapaneseLanguageProvider(
        output: FabricDataOutput,
        registriesFuture: CompletableFuture<HolderLookup.Provider>,
    ) : FabricLanguageProvider(output, "ja_jp", registriesFuture) {
        //?}
        override fun generateTranslations(
            registryLookup: HolderLookup.Provider,
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
//?}
