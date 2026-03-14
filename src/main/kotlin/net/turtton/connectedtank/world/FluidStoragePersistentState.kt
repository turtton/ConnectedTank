package net.turtton.connectedtank.world

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
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

        val adjacentPositions = if (interactedAt != null) {
            // interactedAt が隣接座標に含まれる場合のみ使用、含まれなければ通常の優先度ロジックへフォールバック
            if (allAdjacentPositions.any { it == interactedAt }) {
                listOf(interactedAt)
            } else {
                allAdjacentPositions.sortedWith(compareBy({ it.y }, { it.x }, { it.z }))
            }
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
        val effectiveVariant = if (!primaryStorage.isResourceBlank) primaryStorage.variant else newVariant
        var totalBucketCap = storage.bucketCapacity + primaryStorage.bucketCapacity
        var totalAmount = storage.amount + primaryStorage.amount

        // interactedAt 指定時は他の隣接グループをマージしない
        val idsToMerge = mutableSetOf<UUID>()
        if (interactedAt == null) {
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

    fun removeStorage(pos: BlockPos, world: ServerWorld? = null): TankFluidStorage.ExistingData? {
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
            val newBucketCap = computeGroupCapacity(groupPositions, world)
            val data = if (variant != null && !variant.isBlank && remainingAmount > 0) {
                TankFluidStorage.ExistingData(variant, remainingAmount)
            } else {
                null
            }
            storageMap[uuid] = TankFluidStorage(newBucketCap, data).also { it.onChanged = ::markDirty }
        } else {
            // 分断あり: 液体を均等分配
            splitIntoComponents(components, uuid, variant, remainingAmount, groupPositions.size, world)
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

    private fun computeGroupCapacity(positions: List<BlockPos>, world: ServerWorld?): Int = if (world != null) {
        positions.sumOf { p ->
            (world.getBlockState(p).block as? ConnectedTankBlock)?.tier?.bucketCapacity ?: defaultBucketCapacity
        }
    } else {
        positions.size * defaultBucketCapacity
    }

    private fun computeGroupCapacity(positions: Set<BlockPos>, world: ServerWorld?): Int = if (world != null) {
        positions.sumOf { p ->
            (world.getBlockState(p).block as? ConnectedTankBlock)?.tier?.bucketCapacity ?: defaultBucketCapacity
        }
    } else {
        positions.size * defaultBucketCapacity
    }

    private fun splitIntoComponents(
        components: List<Set<BlockPos>>,
        originalUuid: UUID,
        variant: net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant?,
        remainingAmount: Long,
        remainingTanks: Int,
        world: ServerWorld?,
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
