package net.turtton.connectedtank.block

import java.util.UUID
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.core.HolderLookup
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.core.UUIDUtil
import net.minecraft.core.BlockPos
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
        groupId = newGroupId ?: groupId
        val posCapacity = (level?.getBlockState(worldPosition)?.block as? ConnectedTankBlock)?.tier?.bucketCapacity
            ?: CTServerConfig.instance.tankBucketCapacity
        val posCapacityDroplets = posCapacity.toLong() * FluidConstants.BUCKET
        localFillLevel = if (posCapacityDroplets > 0) clamp(0f, 1f, localShare.toFloat() / posCapacityDroplets) else 0f
        if (variantChanged || amountChanged) {
            waveStartTick = level?.gameTime ?: 0L
        }
        setChanged()
        level?.let { w ->
            val state = w.getBlockState(worldPosition)
            w.sendBlockUpdated(worldPosition, state, state, 3)
        }
    }

    override fun loadAdditional(view: ValueInput) {
        fluidVariant = view.read("variant", FluidVariant.CODEC).orElse(FluidVariant.blank())
        amount = view.getLongOr("amount", 0L)
        capacity = view.getLongOr("capacity", 0L)
        waveStartTick = view.getLongOr("waveStartTick", 0L)
        localFillLevel = view.getFloatOr("localFillLevel", 0f)
        groupId = view.read("groupId", UUIDUtil.AUTHLIB_CODEC).orElse(null)
    }

    override fun saveAdditional(view: ValueOutput) {
        view.store("variant", FluidVariant.CODEC, fluidVariant)
        view.putLong("amount", amount)
        view.putLong("capacity", capacity)
        view.putLong("waveStartTick", waveStartTick)
        view.putFloat("localFillLevel", localFillLevel)
        view.storeNullable("groupId", UUIDUtil.AUTHLIB_CODEC, groupId)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveCustomOnly(registries)
}
