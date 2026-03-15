package net.turtton.connectedtank.block

import java.util.UUID
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
import net.minecraft.util.Uuids
import net.minecraft.util.math.BlockPos
import net.turtton.connectedtank.config.CTServerConfig
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
    var waveStartTick: Long = 0L
        private set
    var groupId: UUID? = null
        private set

    /** グループ全体の充填率 (amount / capacity) */
    val fillLevel: Float
        get() = if (capacity <= 0L) 0f else clamp(0f, 1f, amount.toFloat() / capacity)

    /** 位置ベース分配による、このタンク個別の充填率 */
    var localFillLevel: Float = 0f
        private set

    fun updateFromStorage(storage: TankFluidStorage, localShare: Long = storage.amount, newGroupId: UUID? = null) {
        val variantChanged = fluidVariant != storage.variant
        val amountChanged = amount != storage.amount
        fluidVariant = storage.variant
        amount = storage.amount
        capacity = storage.bucketCapacity.toLong() * FluidConstants.BUCKET
        groupId = newGroupId
        val posCapacity = (world?.getBlockState(pos)?.block as? ConnectedTankBlock)?.tier?.bucketCapacity
            ?: CTServerConfig.instance.tankBucketCapacity
        val posCapacityDroplets = posCapacity.toLong() * FluidConstants.BUCKET
        localFillLevel = if (posCapacityDroplets > 0) clamp(0f, 1f, localShare.toFloat() / posCapacityDroplets) else 0f
        if (variantChanged || amountChanged) {
            waveStartTick = world?.time ?: 0L
        }
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
        waveStartTick = view.getLong("waveStartTick", 0L)
        localFillLevel = view.getFloat("localFillLevel", 0f)
        groupId = view.read("groupId", Uuids.CODEC).orElse(null)
    }

    override fun writeData(view: WriteView) {
        view.put("variant", FluidVariant.CODEC, fluidVariant)
        view.putLong("amount", amount)
        view.putLong("capacity", capacity)
        view.putLong("waveStartTick", waveStartTick)
        view.putFloat("localFillLevel", localFillLevel)
        view.putNullable("groupId", Uuids.CODEC, groupId)
    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(registries: RegistryWrapper.WrapperLookup): NbtCompound = createComponentlessNbt(registries)
}
