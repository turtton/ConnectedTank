package net.turtton.connectedtank

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap
import net.minecraft.client.render.BlockRenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.turtton.connectedtank.block.CTBlockEntityTypes
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlockEntityRenderer

object ConnectedTankClient : ClientModInitializer {
    override fun onInitializeClient() {
        BlockRenderLayerMap.putBlock(CTBlocks.CONNECTED_TANK, BlockRenderLayer.CUTOUT)
        BlockEntityRendererFactories.register(CTBlockEntityTypes.CONNECTED_TANK, ::ConnectedTankBlockEntityRenderer)
    }
}
