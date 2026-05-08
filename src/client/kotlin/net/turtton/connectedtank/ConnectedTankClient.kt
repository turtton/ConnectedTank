package net.turtton.connectedtank

//? if fabric {
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
//? if <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
//?}
//?} else if neoforge {
/*import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent
import net.neoforged.neoforge.common.NeoForge*/
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.turtton.connectedtank.block.CTBlockEntityTypes
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlockEntityRenderer
//? if >=1.21.11 {
/*import net.turtton.connectedtank.block.ConnectedTankRenderState*/
//?}
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.config.SyncedServerConfig
import net.turtton.connectedtank.item.ConnectedTankItemRenderer
import net.turtton.connectedtank.network.ConfigSyncPayload

//? if fabric {
object ConnectedTankClient : ClientModInitializer {
    override fun onInitializeClient() {
        CTClientConfig.load()

        //? if <26.1 {
        CTBlocks.ALL_TANKS.forEach { BlockRenderLayerMap.putBlock(it, ChunkSectionLayer.CUTOUT) }
        //?}
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
//?} else if neoforge {
/*@EventBusSubscriber(value = [Dist.CLIENT], modid = "connectedtank", bus = EventBusSubscriber.Bus.MOD)
object ConnectedTankClient {
    init {
        CTClientConfig.load()
        ConfigSyncPayload.onConfigReceived = { payload ->
            SyncedServerConfig.syncedConfig = CTServerConfig(
                tankBucketCapacity = payload.tankBucketCapacity.coerceIn(
                    1,
                    CTServerConfig.MAX_BUCKET_CAPACITY,
                ),
                tierMultipliers = payload.tierMultipliers.toMutableMap(),
            )
        }
        NeoForge.EVENT_BUS.addListener<ClientPlayerNetworkEvent.LoggingOut> { _ ->
            SyncedServerConfig.syncedConfig = null
        }
    }

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        BlockEntityRenderers.register<_, ConnectedTankRenderState>(
            CTBlockEntityTypes.CONNECTED_TANK,
        ) { context -> ConnectedTankBlockEntityRenderer(context) }
    }

    @SubscribeEvent
    fun registerSpecialRenderers(event: RegisterSpecialModelRendererEvent) {
        event.register(ConnectedTankItemRenderer.ID, ConnectedTankItemRenderer.Unbaked.CODEC)
    }
}*/
//?}
