package net.turtton.connectedtank.block

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.registry.RegistryWrapper
import net.minecraft.storage.ReadView
import net.minecraft.storage.WriteView
import net.minecraft.util.math.BlockPos
import org.joml.Math.clamp

class ConnectedTankBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(CTBlockEntityTypes.CONNECTED_TANK, pos, state) {
    var fluidVariant: FluidVariant = FluidVariant.blank()
        private set
    var amount: Long = 0L
        private set
    var capacity: Long = 0L
        private set

    val fillLevel: Float
        get() = if (capacity <= 0L) 0f else clamp(0f, 1f, amount.toFloat() / capacity)

    fun updateFromStorage(storage: TankFluidStorage) {
        fluidVariant = storage.variant
        amount = storage.amount
        capacity = storage.bucketCapacity.toLong() * FluidConstants.BUCKET
        markDirty()
        world?.let { w ->
            val state = w.getBlockState(pos)
            w.updateListeners(pos, state, state, 3)
        }
    }

    override fun readData(view: ReadView) {
        fluidVariant = view.read("variant", FluidVariant.CODEC).orElse(FluidVariant.blank())
        amount = view.getLong("amount", 0L)
        capacity = view.getLong("capacity", 0L)
    }

    override fun writeData(view: WriteView) {
        view.put("variant", FluidVariant.CODEC, fluidVariant)
        view.putLong("amount", amount)
        view.putLong("capacity", capacity)
    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(registries: RegistryWrapper.WrapperLookup): NbtCompound = createComponentlessNbt(registries)
}
