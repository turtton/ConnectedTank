package net.turtton.connectedtank

import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
import net.minecraft.advancement.AdvancementRequirements
import net.minecraft.advancement.AdvancementRewards
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion
import net.minecraft.block.Block
import net.minecraft.client.data.BlockStateModelGenerator
import net.minecraft.client.data.ItemModelGenerator
import net.minecraft.client.data.Models
import net.minecraft.client.data.TextureMap
import net.minecraft.client.data.TexturedModel
import net.minecraft.data.recipe.RecipeExporter
import net.minecraft.data.recipe.RecipeGenerator
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
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
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.extension.ModIdentifier
import net.turtton.connectedtank.recipe.TankUpgradeRecipe

object ConnectedTankDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::ModelProvider)
        pack.addProvider(::RecipeProvider)
    }

    private class ModelProvider(output: FabricDataOutput) : FabricModelProvider(output) {
        private val glassTextureFactory = TexturedModel.makeFactory(
            { TextureMap.all(Identifier.of("minecraft", "block/glass")) },
            Models.CUBE_ALL,
        )

        override fun generateBlockStateModels(generator: BlockStateModelGenerator) {
            for (block in CTBlocks.ALL_TANKS) {
                generator.registerSingleton(block, glassTextureFactory)
                generator.registerItemModel(block)
            }
        }

        override fun generateItemModels(generator: ItemModelGenerator) {}
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
                    CTBlocks.WOOD_CONNECTED_TANK,
                    ItemTags.PLANKS,
                    "has_planks",
                    conditionsFromTag(ItemTags.PLANKS),
                )
                offerTankUpgrade(
                    CTBlocks.WOOD_CONNECTED_TANK,
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
                offerTankUpgradeItem(
                    CTBlocks.DIAMOND_CONNECTED_TANK,
                    CTBlocks.NETHERITE_CONNECTED_TANK,
                    Items.NETHERITE_INGOT,
                )
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
}
