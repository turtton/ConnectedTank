package net.turtton.connectedtank.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.MinecraftServer
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.extension.ModIdentifier

data class ConfigSyncPayload(
    val tankBucketCapacity: Int,
    val tierMultipliers: Map<String, Int>,
) : CustomPayload {
    override fun getId(): CustomPayload.Id<ConfigSyncPayload> = ID

    companion object {
        val ID: CustomPayload.Id<ConfigSyncPayload> = CustomPayload.Id(ModIdentifier("config_sync"))

        private val TIER_MULTIPLIER_CODEC: PacketCodec<RegistryByteBuf, Map<String, Int>> =
            PacketCodec.ofStatic(
                { buf, map ->
                    buf.writeVarInt(map.size)
                    for ((key, value) in map) {
                        buf.writeString(key)
                        buf.writeVarInt(value)
                    }
                },
                { buf ->
                    val size = buf.readVarInt()
                    buildMap(size) {
                        repeat(size) {
                            put(buf.readString(), buf.readVarInt())
                        }
                    }
                },
            )

        val CODEC: PacketCodec<RegistryByteBuf, ConfigSyncPayload> = PacketCodec.ofStatic(
            { buf, payload ->
                PacketCodecs.VAR_INT.encode(buf, payload.tankBucketCapacity)
                TIER_MULTIPLIER_CODEC.encode(buf, payload.tierMultipliers)
            },
            { buf ->
                val capacity = PacketCodecs.VAR_INT.decode(buf)
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
            for (player in server.playerManager.playerList) {
                ServerPlayNetworking.send(player, payload)
            }
        }
    }
}
