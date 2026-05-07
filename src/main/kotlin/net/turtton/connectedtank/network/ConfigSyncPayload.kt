package net.turtton.connectedtank.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
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
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
            ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
                val config = CTServerConfig.instance
                val payload = ConfigSyncPayload(config.tankBucketCapacity, config.tierMultipliers)
                ServerPlayNetworking.send(handler.player, payload)
            }
        }

        fun broadcastToAll(server: MinecraftServer) {
            val config = CTServerConfig.instance
            val payload = ConfigSyncPayload(config.tankBucketCapacity, config.tierMultipliers)
            for (player in server.playerList.players) {
                ServerPlayNetworking.send(player, payload)
            }
        }
    }
}
