package net.turtton.connectedtank

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.minecraft.client.data.BlockStateModelGenerator
import net.minecraft.client.data.ItemModelGenerator
import net.minecraft.client.data.Models
import net.minecraft.client.data.TextureMap
import net.minecraft.client.data.TexturedModel
import net.minecraft.util.Identifier
import net.turtton.connectedtank.block.CTBlocks

object ConnectedTankDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::ModelProvider)
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
}
