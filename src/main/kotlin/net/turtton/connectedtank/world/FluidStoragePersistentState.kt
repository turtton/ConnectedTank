package net.turtton.connectedtank.world

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Uuids
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateType
import net.turtton.connectedtank.MOD_ID
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.config.CTServerConfig

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
        val allAdjacentPositions = adjacentOffsets
            .map { pos.add(it) }
            .filter { positionalStorageMap.containsKey(it) }

        // interactedAt が隣接座標に含まれる場合のみ有効、含まれなければ通常の優先度ロジックへフォールバック
        val effectiveInteractedAt = interactedAt?.takeIf { target -> allAdjacentPositions.any { it == target } }

        val adjacentPositions = if (effectiveInteractedAt != null) {
            listOf(effectiveInteractedAt)
        } else {
            // 座標優先度: Y昇順 → X昇順 → Z昇順
            allAdjacentPositions.sortedWith(compareBy({ it.y }, { it.x }, { it.z }))
        }

        if (adjacentPositions.isEmpty()) {
            val uuid = UUID.randomUUID()
            positionalStorageMap[pos] = uuid
            storageMap[uuid] = storage.also { it.onChanged = ::markDirty }
            markDirty()
            return
        }

        val newVariant = if (!storage.isResourceBlank) storage.variant else null

        // 座標優先度順に最初の互換グループを primary として選択
        var primaryId: UUID? = null
        for (adjPos in adjacentPositions) {
            val adjId = positionalStorageMap[adjPos] ?: continue
            if (adjId == primaryId) continue
            val adjStorage = storageMap[adjId] ?: continue
            val adjVariant = if (!adjStorage.isResourceBlank) adjStorage.variant else null
            val variants = listOfNotNull(newVariant, adjVariant).distinct()
            if (variants.size <= 1) {
                primaryId = adjId
                break
            }
        }

        if (primaryId == null) {
            val uuid = UUID.randomUUID()
            positionalStorageMap[pos] = uuid
            storageMap[uuid] = storage.also { it.onChanged = ::markDirty }
            markDirty()
            return
        }

        val primaryStorage = storageMap[primaryId]!!
        var effectiveVariant = if (!primaryStorage.isResourceBlank) primaryStorage.variant else newVariant
        var totalBucketCap = storage.bucketCapacity + primaryStorage.bucketCapacity
        var totalAmount = storage.amount + primaryStorage.amount

        // effectiveInteractedAt 指定時は他の隣接グループをマージしない
        val idsToMerge = mutableSetOf<UUID>()
        if (effectiveInteractedAt == null) {
            for (adjPos in adjacentPositions) {
                val adjId = positionalStorageMap[adjPos] ?: continue
                if (adjId == primaryId || adjId in idsToMerge) continue
                val adjStorage = storageMap[adjId] ?: continue
                val adjVariant = if (!adjStorage.isResourceBlank) adjStorage.variant else null
                val variants = listOfNotNull(effectiveVariant, adjVariant).distinct()
                if (variants.size <= 1) {
                    totalBucketCap += adjStorage.bucketCapacity
                    totalAmount += adjStorage.amount
                    idsToMerge.add(adjId)
                    // effectiveVariant が未確定の場合、マージした隣接グループのバリアントを採用
                    if (effectiveVariant == null && adjVariant != null) {
                        effectiveVariant = adjVariant
                    }
                }
            }
        }

        val mergedVariant = listOfNotNull(effectiveVariant, newVariant).distinct().firstOrNull()
        val existingData = mergedVariant?.let { TankFluidStorage.ExistingData(it, totalAmount) }
        val mergedStorage = TankFluidStorage(totalBucketCap, existingData).also { it.onChanged = ::markDirty }

        if (idsToMerge.isNotEmpty()) {
            val keysToRemap = positionalStorageMap.entries
                .filter { it.value in idsToMerge }
                .map { it.key }
            for (key in keysToRemap) {
                positionalStorageMap[key] = primaryId
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

    fun removeStorage(
        pos: BlockPos,
        world: ServerWorld? = null,
        removedBucketCapacity: Int? = null,
    ): TankFluidStorage.ExistingData? {
        val uuid = positionalStorageMap.remove(pos) ?: return null
        val existing = storageMap[uuid]
        val variant = existing?.variant
        val amount = existing?.amount ?: 0L

        val groupPositions = positionalStorageMap.entries
            .filter { it.value == uuid }
            .map { it.key }

        val allPositions = groupPositions + pos
        val capacityOverrides = if (removedBucketCapacity != null) mapOf(pos to removedBucketCapacity) else emptyMap()
        val allShares = calculatePositionShares(allPositions, amount, world, capacityOverrides)
        val removedShare = allShares[pos] ?: 0L
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
            val newBucketCap = computeGroupCapacity(groupPositions, world)
            val data = if (variant != null && !variant.isBlank && remainingAmount > 0) {
                TankFluidStorage.ExistingData(variant, remainingAmount)
            } else {
                null
            }
            storageMap[uuid] = TankFluidStorage(newBucketCap, data).also { it.onChanged = ::markDirty }
        } else {
            // 分断あり: 位置ベースで液体を分配
            val remainingShares = calculatePositionShares(groupPositions, remainingAmount, world)
            splitIntoComponents(components, uuid, variant, remainingShares, world)
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

    private fun computeGroupCapacity(positions: Iterable<BlockPos>, world: ServerWorld?): Int = if (world != null) {
        positions.sumOf { p ->
            (world.getBlockState(p).block as? ConnectedTankBlock)?.tier?.bucketCapacity ?: defaultBucketCapacity
        }
    } else {
        positions.count() * defaultBucketCapacity
    }

    private fun splitIntoComponents(
        components: List<Set<BlockPos>>,
        originalUuid: UUID,
        variant: net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant?,
        positionShares: Map<BlockPos, Long>,
        world: ServerWorld?,
    ) {
        // 最大の成分に元の UUID を再利用
        val sorted = components.sortedByDescending { it.size }

        for ((index, component) in sorted.withIndex()) {
            val componentAmount = component.sumOf { positionShares[it] ?: 0L }

            val newBucketCap = computeGroupCapacity(component, world)
            val data = if (variant != null && !variant.isBlank && componentAmount > 0) {
                TankFluidStorage.ExistingData(variant, componentAmount)
            } else {
                null
            }
            val storage = TankFluidStorage(newBucketCap, data).also { it.onChanged = ::markDirty }

            if (index == 0) {
                // 最大の成分は元の UUID を再利用
                storageMap[originalUuid] = storage
            } else {
                val newUuid = UUID.randomUUID()
                storageMap[newUuid] = storage
                for (p in component) {
                    positionalStorageMap[p] = newUuid
                }
            }
        }
    }

    fun calculateShare(
        pos: BlockPos,
        world: ServerWorld?,
        selfBucketCapacity: Int? = null,
    ): Long {
        val overrides = if (selfBucketCapacity != null) mapOf(pos to selfBucketCapacity) else emptyMap()
        return calculateGroupShares(pos, world, overrides)[pos] ?: 0L
    }

    fun calculateGroupShares(
        pos: BlockPos,
        world: ServerWorld?,
        capacityOverrides: Map<BlockPos, Int> = emptyMap(),
    ): Map<BlockPos, Long> {
        val uuid = positionalStorageMap[pos] ?: return emptyMap()
        val storage = storageMap[uuid] ?: return emptyMap()
        if (storage.amount <= 0L) return emptyMap()

        val allPositions = positionalStorageMap.entries
            .filter { it.value == uuid }
            .map { it.key }

        return calculatePositionShares(allPositions, storage.amount, world, capacityOverrides)
    }

    private fun calculatePositionShares(
        positions: List<BlockPos>,
        totalAmount: Long,
        world: ServerWorld?,
        capacityOverrides: Map<BlockPos, Int> = emptyMap(),
    ): Map<BlockPos, Long> {
        if (positions.isEmpty() || totalAmount <= 0) return positions.associateWith { 0L }

        val sorted = positions.sortedWith(compareBy({ it.y }, { it.x }, { it.z }))
        val byLevel = sorted.groupBy { it.y }
        val levels = byLevel.keys.sorted()

        val shares = mutableMapOf<BlockPos, Long>()
        var remaining = totalAmount

        for (y in levels) {
            val levelPositions = byLevel[y]!!
            val levelCapacities = levelPositions.map { getPositionCapacityDroplets(it, world, capacityOverrides) }
            val levelTotalCapacity = levelCapacities.sum()
            val levelFill = minOf(remaining, levelTotalCapacity)

            if (levelFill <= 0) {
                for (p in levelPositions) shares[p] = 0L
                continue
            }

            distributeProportion(levelPositions, levelCapacities, levelTotalCapacity, levelFill, shares)
            remaining -= levelFill
        }

        // 設定変更等で amount > totalCapacity の場合、余剰分を最下層の先頭タンクに加算
        if (remaining > 0) {
            val firstPos = sorted.first()
            shares[firstPos] = (shares[firstPos] ?: 0L) + remaining
        }

        return shares
    }

    private fun distributeProportion(
        positions: List<BlockPos>,
        capacities: List<Long>,
        totalCapacity: Long,
        amount: Long,
        shares: MutableMap<BlockPos, Long>,
    ) {
        var cumulativeCapacity = 0L
        var previousCumulativeShare = 0L
        for (i in positions.indices) {
            cumulativeCapacity += capacities[i]
            val cumulativeShare = amount * cumulativeCapacity / totalCapacity
            shares[positions[i]] = cumulativeShare - previousCumulativeShare
            previousCumulativeShare = cumulativeShare
        }
    }

    private fun getPositionCapacityDroplets(
        pos: BlockPos,
        world: ServerWorld?,
        capacityOverrides: Map<BlockPos, Int> = emptyMap(),
    ): Long {
        val bucketCap = capacityOverrides[pos] ?: if (world != null) {
            (world.getBlockState(pos).block as? ConnectedTankBlock)?.tier?.bucketCapacity ?: defaultBucketCapacity
        } else {
            defaultBucketCapacity
        }
        return bucketCap.toLong() * FluidConstants.BUCKET
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
        private val defaultBucketCapacity: Int
            get() = CTServerConfig.instance.tankBucketCapacity

        val CODEC: Codec<FluidStoragePersistentState> = RecordCodecBuilder.create {
            it.group(
                PositionalStorageEntry.MAP_CODEC.fieldOf("positionalStorageMap").forGetter(FluidStoragePersistentState::positionalStorageMap),
                Codec.unboundedMap(Uuids.CODEC, TankFluidStorage.CODEC).fieldOf("storageMap").forGetter(FluidStoragePersistentState::storageMap),
            ).apply(it, ::FluidStoragePersistentState)
        }
        val TYPE: PersistentStateType<FluidStoragePersistentState> = PersistentStateType("${MOD_ID}_fluid_storage", ::FluidStoragePersistentState, CODEC, null)
    }
}
