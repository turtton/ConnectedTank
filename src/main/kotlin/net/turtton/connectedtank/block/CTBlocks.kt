package net.turtton.connectedtank.block

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.turtton.connectedtank.extension.ModIdentifier
import net.turtton.connectedtank.world.FluidStoragePersistentState

object CTBlocks {
    val CONNECTED_TANK = register("connected_tank", ::ConnectedTankBlock)

    private fun register(
        name: String,
        factory: (AbstractBlock.Settings) -> Block,
        settingsFactory: AbstractBlock.Settings.() -> Unit = {},
    ): Block {
        val blockKey = RegistryKey.of(RegistryKeys.BLOCK, ModIdentifier(name))
        val settings = AbstractBlock.Settings.create().nonOpaque().apply(settingsFactory)
        val block = factory(settings.registryKey(blockKey))
        return Registry.register(Registries.BLOCK, blockKey, block)
    }

    fun init() {
        FluidStorage.SIDED.registerForBlocks({ world, pos, _, _, _ ->
            val serverWorld = world as? ServerWorld ?: return@registerForBlocks null
            val state = serverWorld.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            val storage = state.getStorage(pos) ?: TankFluidStorage().also { state.addStorage(pos, it) }
            storage.onChanged = {
                state.markDirty()
                syncGroupBlockEntities(serverWorld, pos, state)
            }
            storage
        }, CONNECTED_TANK)
    }

    internal fun syncGroupBlockEntities(
        world: ServerWorld,
        pos: BlockPos,
        state: FluidStoragePersistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE),
    ) {
        val storage = state.getStorage(pos) ?: return
        for (groupPos in state.getGroupPositions(pos)) {
            val blockEntity = world.getBlockEntity(groupPos) as? ConnectedTankBlockEntity ?: continue
            blockEntity.updateFromStorage(storage)
        }
    }
}
