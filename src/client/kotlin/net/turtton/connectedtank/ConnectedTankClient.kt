package net.turtton.connectedtank

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import net.turtton.connectedtank.block.CTBlockEntityTypes
import net.turtton.connectedtank.block.ConnectedTankBlockEntityRenderer

object ConnectedTankClient : ClientModInitializer {
    override fun onInitializeClient() {
        BlockEntityRendererFactories.register(CTBlockEntityTypes.CONNECTED_TANK, ::ConnectedTankBlockEntityRenderer)
    }
}
