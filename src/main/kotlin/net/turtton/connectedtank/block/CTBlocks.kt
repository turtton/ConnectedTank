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
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.extension.ModIdentifier
import net.turtton.connectedtank.world.FluidStoragePersistentState

object CTBlocks {
    val CONNECTED_TANK = register(TankTier.BASE)
    val WOOD_CONNECTED_TANK = register(TankTier.WOOD)
    val STONE_CONNECTED_TANK = register(TankTier.STONE)
    val COPPER_CONNECTED_TANK = register(TankTier.COPPER)
    val IRON_CONNECTED_TANK = register(TankTier.IRON)
    val GOLD_CONNECTED_TANK = register(TankTier.GOLD)
    val DIAMOND_CONNECTED_TANK = register(TankTier.DIAMOND)
    val NETHERITE_CONNECTED_TANK = register(TankTier.NETHERITE)

    val ALL_TANKS: List<Block> = listOf(
        CONNECTED_TANK,
        WOOD_CONNECTED_TANK,
        STONE_CONNECTED_TANK,
        COPPER_CONNECTED_TANK,
        IRON_CONNECTED_TANK,
        GOLD_CONNECTED_TANK,
        DIAMOND_CONNECTED_TANK,
        NETHERITE_CONNECTED_TANK,
    )

    private val tankBlockSet: Set<Block> = ALL_TANKS.toSet()

    fun isConnectedTank(block: Block): Boolean = block in tankBlockSet

    private fun register(tier: TankTier): Block {
        val blockKey = RegistryKey.of(RegistryKeys.BLOCK, ModIdentifier(tier.id))
        val settings = AbstractBlock.Settings.create().nonOpaque().registryKey(blockKey)
        val block = ConnectedTankBlock(tier, settings)
        return Registry.register(Registries.BLOCK, blockKey, block)
    }

    fun init() {
        FluidStorage.SIDED.registerForBlocks({ world, pos, _, _, _ ->
            val serverWorld = world as? ServerWorld ?: return@registerForBlocks null
            val state = serverWorld.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            val storage = state.getStorage(pos) ?: run {
                val block = serverWorld.getBlockState(pos).block as? ConnectedTankBlock
                val cap = block?.tier?.bucketCapacity ?: CTServerConfig.instance.tankBucketCapacity
                TankFluidStorage(cap).also { state.addStorage(pos, it) }
            }
            storage.onChanged = {
                state.markDirty()
                syncGroupBlockEntities(serverWorld, pos, state)
            }
            storage
        }, *ALL_TANKS.toTypedArray())
    }

    fun syncGroupBlockEntities(
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
