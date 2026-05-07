package net.turtton.connectedtank

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.turtton.connectedtank.block.CTBlockEntityTypes
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlockEntityRenderer
//? if >=1.21.11
/*import net.turtton.connectedtank.block.ConnectedTankRenderState*/
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.config.SyncedServerConfig
import net.turtton.connectedtank.item.ConnectedTankItemRenderer
import net.turtton.connectedtank.network.ConfigSyncPayload

object ConnectedTankClient : ClientModInitializer {
    override fun onInitializeClient() {
        CTClientConfig.load()

        CTBlocks.ALL_TANKS.forEach { BlockRenderLayerMap.putBlock(it, ChunkSectionLayer.CUTOUT) }
        //? if >=1.21.11 {
        /*BlockEntityRenderers.register<_, ConnectedTankRenderState>(
            CTBlockEntityTypes.CONNECTED_TANK,
        ) { context -> ConnectedTankBlockEntityRenderer(context) }*/
        //?} else {
        BlockEntityRenderers.register(
            CTBlockEntityTypes.CONNECTED_TANK,
        ) { context -> ConnectedTankBlockEntityRenderer(context) }
        //?}
        ConnectedTankItemRenderer.register()

        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID) { payload, _ ->
            SyncedServerConfig.syncedConfig = CTServerConfig(
                tankBucketCapacity = payload.tankBucketCapacity.coerceIn(
                    1,
                    CTServerConfig.MAX_BUCKET_CAPACITY,
                ),
                tierMultipliers = payload.tierMultipliers.toMutableMap(),
            )
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SyncedServerConfig.syncedConfig = null
        }
    }
}
