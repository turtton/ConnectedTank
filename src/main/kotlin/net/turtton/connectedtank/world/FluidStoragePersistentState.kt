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

    private val adjacentOffsets = listOf(
        BlockPos(-1, 0, 0),
        BlockPos(1, 0, 0),
        BlockPos(0, 0, -1),
        BlockPos(0, 0, 1),
        BlockPos(0, -1, 0),
        BlockPos(0, 1, 0),
    )

    fun addStorage(pos: BlockPos, storage: TankFluidStorage, interactedAt: BlockPos? = null) {
        val neighborIds = if (interactedAt != null) {
            listOfNotNull(positionalStorageMap[interactedAt])
        } else {
            adjacentOffsets.mapNotNull { positionalStorageMap[pos.add(it)] }.distinct()
        }

        if (neighborIds.isEmpty()) {
            val uuid = UUID.randomUUID()
            positionalStorageMap[pos] = uuid
            storageMap[uuid] = storage.also { it.onChanged = ::markDirty }
            markDirty()
            return
        }

        val neighborStorages = neighborIds.mapNotNull { id -> storageMap[id]?.let { id to it } }
        val nonBlankVariants = neighborStorages.mapNotNull { (_, s) ->
            if (s.isResourceBlank) null else s.variant
        }.distinct()
        val newVariant = if (!storage.isResourceBlank) storage.variant else null
        val allVariants = (nonBlankVariants + listOfNotNull(newVariant)).distinct()

        if (allVariants.size > 1) {
            val uuid = UUID.randomUUID()
            positionalStorageMap[pos] = uuid
            storageMap[uuid] = storage.also { it.onChanged = ::markDirty }
            markDirty()
            return
        }

        val primaryId = neighborIds.first()
        val totalBucketCap = storage.bucketCapacity + neighborStorages.sumOf { (_, s) -> s.bucketCapacity }
        val totalAmount = (storage.amount + neighborStorages.sumOf { (_, s) -> s.amount })
            .coerceAtMost(totalBucketCap.toLong() * FluidConstants.BUCKET)
        val mergedVariant = allVariants.firstOrNull()
        val existingData = mergedVariant?.let { TankFluidStorage.ExistingData(it, totalAmount) }
        val mergedStorage = TankFluidStorage(totalBucketCap, existingData).also { it.onChanged = ::markDirty }

        // 全グループを primaryId にリマップ
        for ((oldId, _) in neighborStorages) {
            if (oldId != primaryId) {
                storageMap.remove(oldId)
                for ((p, id) in positionalStorageMap) {
                    if (id == oldId) {
                        positionalStorageMap[p] = primaryId
                    }
                }
            }
        }
        storageMap[primaryId] = mergedStorage
        positionalStorageMap[pos] = primaryId

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
        val TYPE: PersistentStateType<FluidStoragePersistentState> = PersistentStateType("${MOD_ID}_fluid_storage", ::FluidStoragePersistentState, CODEC, null)
    }
}
