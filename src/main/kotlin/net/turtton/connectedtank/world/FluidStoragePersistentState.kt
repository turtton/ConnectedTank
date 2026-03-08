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

    fun getStorage(pos: BlockPos): TankFluidStorage? = positionalStorageMap[pos]?.let(storageMap::get)

    fun getGroupPositions(pos: BlockPos): List<BlockPos> {
        val uuid = positionalStorageMap[pos] ?: return emptyList()
        return positionalStorageMap.entries
            .filter { it.value == uuid }
            .map { it.key }
    }

    private val adjacentOffsets = ADJACENT_OFFSETS

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

        // 全グループを primaryId にリマップ（単一パス）
        val idsToMerge = neighborStorages
            .map { (id, _) -> id }
            .filter { it != primaryId }
            .toSet()
        if (idsToMerge.isNotEmpty()) {
            for ((p, id) in positionalStorageMap) {
                if (id in idsToMerge) {
                    positionalStorageMap[p] = primaryId
                }
            }
            for (oldId in idsToMerge) {
                storageMap.remove(oldId)
            }
        }
        storageMap[primaryId] = mergedStorage
        positionalStorageMap[pos] = primaryId

        markDirty()
    }

    fun getGroupSize(pos: BlockPos): Int {
        val uuid = positionalStorageMap[pos] ?: return 0
        return positionalStorageMap.values.count { it == uuid }
    }

    fun removeStorage(pos: BlockPos): TankFluidStorage.ExistingData? {
        val uuid = positionalStorageMap.remove(pos) ?: return null
        val existing = storageMap[uuid]
        val variant = existing?.variant
        val amount = existing?.amount ?: 0L

        val groupPositions = positionalStorageMap.entries
            .filter { it.value == uuid }
            .map { it.key }

        val totalTanks = groupPositions.size + 1
        val perTank = amount / totalTanks
        val removedShare = perTank + if (amount % totalTanks > 0) 1L else 0L
        val remainingAmount = amount - removedShare

        val removedData = if (variant != null && !variant.isBlank && removedShare > 0) {
            TankFluidStorage.ExistingData(variant, removedShare)
        } else {
            null
        }

        if (groupPositions.isEmpty()) {
            storageMap.remove(uuid)
            markDirty()
            return removedData
        }

        // BFS で連結成分を検出
        val components = findConnectedComponents(groupPositions)

        if (components.size == 1) {
            // 分断なし
            val newBucketCap = groupPositions.size * DEFAULT_BUCKET_CAPACITY
            val data = if (variant != null && !variant.isBlank && remainingAmount > 0) {
                TankFluidStorage.ExistingData(variant, remainingAmount)
            } else {
                null
            }
            storageMap[uuid] = TankFluidStorage(newBucketCap, data).also { it.onChanged = ::markDirty }
        } else {
            // 分断あり: 液体を均等分配
            splitIntoComponents(components, uuid, variant, remainingAmount, groupPositions.size)
        }

        markDirty()
        return removedData
    }

    private fun findConnectedComponents(positions: List<BlockPos>): List<Set<BlockPos>> {
        val posSet = positions.toMutableSet()
        val components = mutableListOf<Set<BlockPos>>()

        while (posSet.isNotEmpty()) {
            val start = posSet.first()
            val reachable = mutableSetOf(start)
            val queue = ArrayDeque<BlockPos>()
            queue.add(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (offset in adjacentOffsets) {
                    val neighbor = current.add(offset)
                    if (neighbor in posSet && neighbor !in reachable) {
                        reachable.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }

            components.add(reachable)
            posSet.removeAll(reachable)
        }

        return components
    }

    private fun splitIntoComponents(
        components: List<Set<BlockPos>>,
        originalUuid: UUID,
        variant: net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant?,
        remainingAmount: Long,
        remainingTanks: Int,
    ) {
        val basePerTank = remainingAmount / remainingTanks
        var extraTanks = (remainingAmount % remainingTanks).toInt()

        // 最大の成分に元の UUID を再利用
        val sorted = components.sortedByDescending { it.size }

        for ((index, component) in sorted.withIndex()) {
            val componentBase = basePerTank * component.size
            val componentExtra = minOf(extraTanks, component.size)
            val componentAmount = componentBase + componentExtra
            extraTanks -= componentExtra

            val newBucketCap = component.size * DEFAULT_BUCKET_CAPACITY
            val data = if (variant != null && !variant.isBlank && componentAmount > 0) {
                TankFluidStorage.ExistingData(variant, componentAmount)
            } else {
                null
            }
            val storage = TankFluidStorage(newBucketCap, data).also { it.onChanged = ::markDirty }

            if (index == 0) {
                // 最大の成分は元の UUID を再利用
                storageMap[originalUuid] = storage
                // 他の成分のポジションだけリマップが必要（後続で処理）
            } else {
                val newUuid = UUID.randomUUID()
                storageMap[newUuid] = storage
                for (p in component) {
                    positionalStorageMap[p] = newUuid
                }
            }
        }
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
        val ADJACENT_OFFSETS = listOf(
            BlockPos(-1, 0, 0),
            BlockPos(1, 0, 0),
            BlockPos(0, 0, -1),
            BlockPos(0, 0, 1),
            BlockPos(0, -1, 0),
            BlockPos(0, 1, 0),
        )
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
