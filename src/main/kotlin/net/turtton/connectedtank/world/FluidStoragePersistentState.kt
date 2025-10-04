package net.turtton.connectedtank.world

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.UUID
import net.minecraft.util.Uuids
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateType
import net.turtton.connectedtank.MOD_ID
import net.turtton.connectedtank.block.TankFluidStorage

class FluidStoragePersistentState(
    val positionalStorageMap: MutableMap<BlockPos, UUID> = mutableMapOf(),
    val storageMap: MutableMap<UUID, TankFluidStorage> = mutableMapOf(),
) : PersistentState() {
    fun getStorage(pos: BlockPos): TankFluidStorage? = positionalStorageMap[pos]?.let(storageMap::get)

    fun addStorage(pos: BlockPos, storage: TankFluidStorage) {
        val uuid = UUID.randomUUID()
        positionalStorageMap[pos] = uuid
        storageMap[uuid] = storage
    }

    fun removeStorage(pos: BlockPos) {
        // TODO: Expect combined storage
        val uuid = positionalStorageMap.remove(pos)
        if (uuid != null) {
            storageMap.remove(uuid)
        }
    }

    fun extendStorage(pos: BlockPos, targetPos: BlockPos, storage: TankFluidStorage) {
        TODO()
    }

    companion object {
        val CODEC: Codec<FluidStoragePersistentState> = RecordCodecBuilder.create {
            it.group(
                Codec.unboundedMap(BlockPos.CODEC, Uuids.CODEC).fieldOf("positionalStorageMap").forGetter(FluidStoragePersistentState::positionalStorageMap),
                Codec.unboundedMap(Uuids.CODEC, TankFluidStorage.CODEC).fieldOf("storageMap").forGetter(FluidStoragePersistentState::storageMap),
                // FIXME: codec creates immutable map
            ).apply(it, ::FluidStoragePersistentState)
        }
        val TYPE: PersistentStateType<FluidStoragePersistentState> = PersistentStateType(MOD_ID, ::FluidStoragePersistentState, CODEC, null)
    }
}
