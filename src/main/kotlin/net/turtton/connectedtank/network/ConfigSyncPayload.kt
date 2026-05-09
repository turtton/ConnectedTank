package net.turtton.connectedtank.network

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
//?} else if neoforge {
/*import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.minecraft.server.level.ServerPlayer*/
//?}
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.extension.ModIdentifier

data class ConfigSyncPayload(
    val tankBucketCapacity: Int,
    val tierMultipliers: Map<String, Int>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ConfigSyncPayload> = ID

    companion object {
        val ID: CustomPacketPayload.Type<ConfigSyncPayload> = CustomPacketPayload.Type(ModIdentifier("config_sync"))

        var onConfigReceived: ((ConfigSyncPayload) -> Unit)? = null

        private val TIER_MULTIPLIER_CODEC: StreamCodec<RegistryFriendlyByteBuf, Map<String, Int>> =
            StreamCodec.of(
                { buf, map ->
                    buf.writeVarInt(map.size)
                    for ((key, value) in map) {
                        buf.writeUtf(key)
                        buf.writeVarInt(value)
                    }
                },
                { buf ->
                    val size = buf.readVarInt()
                    buildMap(size) {
                        repeat(size) {
                            put(buf.readUtf(), buf.readVarInt())
                        }
                    }
                },
            )

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> = StreamCodec.of(
            { buf, payload ->
                ByteBufCodecs.VAR_INT.encode(buf, payload.tankBucketCapacity)
                TIER_MULTIPLIER_CODEC.encode(buf, payload.tierMultipliers)
            },
            { buf ->
                val capacity = ByteBufCodecs.VAR_INT.decode(buf)
                val multipliers = TIER_MULTIPLIER_CODEC.decode(buf)
                ConfigSyncPayload(capacity, multipliers)
            },
        )

        fun registerServer() {
            //? if fabric {
            //? if >=26.1 {
            /*PayloadTypeRegistry.clientboundPlay().register(ID, CODEC)*/
            //?} else {
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
            //?}
            ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
                val config = CTServerConfig.instance
                val payload = ConfigSyncPayload(config.tankBucketCapacity, config.tierMultipliers)
                ServerPlayNetworking.send(handler.player, payload)
            }
            //?} else if neoforge {
            /*NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent> { event ->
                val player = event.entity as? ServerPlayer ?: return@addListener
                val config = CTServerConfig.instance
                val payload = ConfigSyncPayload(config.tankBucketCapacity, config.tierMultipliers)
                try {
                    PacketDistributor.sendToPlayer(player, payload)
                } catch (_: UnsupportedOperationException) {
                }
            }*/
            //?}
        }

        //? if neoforge {
        /*@SubscribeEvent
        @JvmStatic
        fun registerPayloadHandler(event: RegisterPayloadHandlersEvent) {
            event.registrar("1").playToClient(ID, CODEC) { payload, _ -> onConfigReceived?.invoke(payload) }
        }*/
        //?}

        fun broadcastToAll(server: MinecraftServer) {
            val config = CTServerConfig.instance
            val payload = ConfigSyncPayload(config.tankBucketCapacity, config.tierMultipliers)
            for (player in server.playerList.players) {
                //? if fabric {
                ServerPlayNetworking.send(player, payload)
                //?} else if neoforge {
                /*PacketDistributor.sendToPlayer(player, payload)*/
                //?}
            }
        }
    }
}
