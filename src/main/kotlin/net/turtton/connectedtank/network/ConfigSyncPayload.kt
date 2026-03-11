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

data class ConfigSyncPayload(val tankBucketCapacity: Int) : CustomPayload {
    override fun getId(): CustomPayload.Id<ConfigSyncPayload> = ID

    companion object {
        val ID: CustomPayload.Id<ConfigSyncPayload> = CustomPayload.Id(ModIdentifier("config_sync"))
        val CODEC: PacketCodec<RegistryByteBuf, ConfigSyncPayload> = PacketCodec.tuple(
            PacketCodecs.VAR_INT,
            ConfigSyncPayload::tankBucketCapacity,
            ::ConfigSyncPayload,
        )

        fun registerServer() {
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
            ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
                val payload = ConfigSyncPayload(CTServerConfig.instance.tankBucketCapacity)
                ServerPlayNetworking.send(handler.player, payload)
            }
        }

        fun broadcastToAll(server: MinecraftServer) {
            val payload = ConfigSyncPayload(CTServerConfig.instance.tankBucketCapacity)
            for (player in server.playerManager.playerList) {
                ServerPlayNetworking.send(player, payload)
            }
        }
    }
}
