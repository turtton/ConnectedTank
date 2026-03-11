package net.turtton.connectedtank

import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
import net.minecraft.client.data.BlockStateModelGenerator
import net.minecraft.client.data.ItemModelGenerator
import net.minecraft.client.data.Models
import net.minecraft.client.data.TextureMap
import net.minecraft.client.data.TexturedModel
import net.minecraft.data.recipe.RecipeExporter
import net.minecraft.data.recipe.RecipeGenerator
import net.minecraft.item.Items
import net.minecraft.recipe.book.RecipeCategory
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.tag.ItemTags
import net.minecraft.util.Identifier
import net.turtton.connectedtank.block.CTBlocks

object ConnectedTankDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::ModelProvider)
        pack.addProvider(::RecipeProvider)
    }

    private class ModelProvider(output: FabricDataOutput) : FabricModelProvider(output) {
        override fun generateBlockStateModels(generator: BlockStateModelGenerator) {
            generator.registerSingleton(
                CTBlocks.CONNECTED_TANK,
                TexturedModel.makeFactory(
                    { TextureMap.all(Identifier.of("minecraft", "block/glass")) },
                    Models.CUBE_ALL,
                ),
            )
            generator.registerItemModel(CTBlocks.CONNECTED_TANK)
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
                createShaped(RecipeCategory.DECORATIONS, CTBlocks.CONNECTED_TANK)
                    .pattern("PGP")
                    .pattern("GPG")
                    .pattern("PGP")
                    .input('P', ItemTags.PLANKS)
                    .input('G', Items.GLASS)
                    .criterion("has_planks", conditionsFromTag(ItemTags.PLANKS))
                    .criterion(hasItem(Items.GLASS), conditionsFromItem(Items.GLASS))
                    .offerTo(exporter)
            }
        }
    }
}
