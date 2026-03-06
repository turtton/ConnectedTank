package net.turtton.connectedtank.world

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.minecraft.util.Uuids
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateType
import net.turtton.connectedtank.MOD_ID
import net.turtton.connectedtank.block.TankFluidStorage

class FluidStoragePersistentState(
    positionalStorageMap: Map<BlockPos, UUID> = mapOf(),
    storageMap: Map<UUID, TankFluidStorage> = mapOf(),
) : PersistentState() {
    private val positionalStorageMap: MutableMap<BlockPos, UUID> = positionalStorageMap.toMutableMap()
    private val storageMap: MutableMap<UUID, TankFluidStorage> = storageMap.toMutableMap()

    fun getStorage(pos: BlockPos): TankFluidStorage? = positionalStorageMap[pos]?.let(storageMap::get)?.also {
        it.onChanged = ::markDirty
    }

    fun addStorage(pos: BlockPos, storage: TankFluidStorage, interactedAt: BlockPos? = null) {
        val storageId = interactedAt?.let(positionalStorageMap::get)
            ?: listOf(
                pos.add(-1, 0, 0),
                pos.add(1, 0, 0),
                pos.add(0, 0, -1),
                pos.add(0, 0, 1),
                pos.add(0, -1, 0),
                pos.add(0, 1, 0),
            ).firstNotNullOfOrNull(positionalStorageMap::get)
        val existingStorage = storageId?.let(storageMap::get)
        if (existingStorage != null && (storage.isResourceBlank || storage.variant == existingStorage.variant)) {
            val newBucketCap = storage.bucketCapacity + existingStorage.bucketCapacity
            val existingData = TankFluidStorage.ExistingData(existingStorage.variant, existingStorage.amount)
            val newStorage = TankFluidStorage(newBucketCap, existingData).also { it.onChanged = ::markDirty }
            storageMap[storageId] = newStorage
            positionalStorageMap[pos] = storageId
        } else {
            val uuid = UUID.randomUUID()
            positionalStorageMap[pos] = uuid
            storageMap[uuid] = storage.also { it.onChanged = ::markDirty }
        }

        markDirty()
    }

    fun removeStorage(pos: BlockPos) {
        val uuid = positionalStorageMap.remove(pos) ?: return
        val remainingCount = positionalStorageMap.values.count { it == uuid }
        if (remainingCount == 0) {
            storageMap.remove(uuid)
        } else {
            val existing = storageMap[uuid]
            if (existing != null) {
                val newBucketCap = existing.bucketCapacity - DEFAULT_BUCKET_CAPACITY
                val amount = existing.amount.coerceAtMost(newBucketCap.toLong() * FluidConstants.BUCKET)
                val data = if (existing.isResourceBlank) null else TankFluidStorage.ExistingData(existing.variant, amount)
                storageMap[uuid] = TankFluidStorage(newBucketCap, data).also { it.onChanged = ::markDirty }
            }
        }
        markDirty()
    }

    private data class PositionalStorageEntry(val pos: BlockPos, val id: UUID) {
        companion object {
            val CODEC: Codec<PositionalStorageEntry> = RecordCodecBuilder.create {
                it.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(PositionalStorageEntry::pos),
                    Uuids.CODEC.fieldOf("id").forGetter(PositionalStorageEntry::id),
                ).apply(it, ::PositionalStorageEntry)
            }

            val MAP_CODEC: Codec<Map<BlockPos, UUID>> = CODEC.listOf()
                .xmap(
                    { it.associate { (pos, id) -> pos to id } },
                    { it.entries.map { (pos, id) -> PositionalStorageEntry(pos, id) } },
                )
        }
    }

    companion object {
        private const val DEFAULT_BUCKET_CAPACITY = 32

        val CODEC: Codec<FluidStoragePersistentState> = RecordCodecBuilder.create {
            it.group(
                PositionalStorageEntry.MAP_CODEC.fieldOf("positionalStorageMap").forGetter(FluidStoragePersistentState::positionalStorageMap),
                Codec.unboundedMap(Uuids.CODEC, TankFluidStorage.CODEC).fieldOf("storageMap").forGetter(FluidStoragePersistentState::storageMap),
            ).apply(it, ::FluidStoragePersistentState)
        }
        val TYPE: PersistentStateType<FluidStoragePersistentState> = PersistentStateType(MOD_ID, ::FluidStoragePersistentState, CODEC, null)
    }
}
