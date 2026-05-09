package net.turtton.connectedtank.block

//? if fabric {
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage
//?}
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.Block
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.extension.ModIdentifier
import net.turtton.connectedtank.world.FluidStoragePersistentState

object CTBlocks {
    val CONNECTED_TANK = register(TankTier.BASE)
    val STONE_CONNECTED_TANK = register(TankTier.STONE)
    val COPPER_CONNECTED_TANK = register(TankTier.COPPER)
    val IRON_CONNECTED_TANK = register(TankTier.IRON)
    val GOLD_CONNECTED_TANK = register(TankTier.GOLD)
    val DIAMOND_CONNECTED_TANK = register(TankTier.DIAMOND)
    val NETHERITE_CONNECTED_TANK = register(TankTier.NETHERITE)

    val ALL_TANKS: List<Block> = listOf(
        CONNECTED_TANK,
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
        val blockKey = ResourceKey.create(Registries.BLOCK, ModIdentifier(tier.id))
        val settings = BlockBehaviour.Properties.of()
            .noOcclusion()
            .strength(tier.hardness)
            .isValidSpawn { _, _, _, _ -> false }
            .isRedstoneConductor { _, _, _ -> false }
            .isSuffocating { _, _, _ -> false }
            .isViewBlocking { _, _, _ -> false }
            .setId(blockKey)
        val block = ConnectedTankBlock(tier, settings)
        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block)
    }

    fun init() {
        //? if fabric {
        FluidStorage.SIDED.registerForBlocks({ world, pos, _, _, _ ->
            val serverWorld = world as? ServerLevel ?: return@registerForBlocks null
            val state = serverWorld.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
            val storage = state.getStorage(pos) ?: run {
                val block = serverWorld.getBlockState(pos).block as? ConnectedTankBlock
                val cap = block?.tier?.bucketCapacity ?: CTServerConfig.instance.tankBucketCapacity
                TankFluidStorage(cap).also { state.addStorage(pos, it) }
            }
            storage.onChanged = {
                state.setDirty()
                syncGroupBlockEntities(serverWorld, pos, state)
            }
            storage
        }, *ALL_TANKS.toTypedArray())
        //?}
    }

    //? if neoforge {
    /*@net.neoforged.bus.api.SubscribeEvent
    @JvmStatic
    fun registerCapabilities(event: net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent) {
        event.registerBlock(
            net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
            { world, pos, state, _, _ ->
                val serverWorld = world as? ServerLevel ?: return@registerBlock null
                val persistentState = serverWorld.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
                val storage = persistentState.getStorage(pos) ?: run {
                    val block = state.block as? ConnectedTankBlock
                    val cap = block?.tier?.bucketCapacity ?: CTServerConfig.instance.tankBucketCapacity
                    TankFluidStorage(cap).also { persistentState.addStorage(pos, it) }
                }
                storage.onChanged = {
                    persistentState.setDirty()
                    syncGroupBlockEntities(serverWorld, pos, persistentState)
                }
                storage
            },
     *ALL_TANKS.toTypedArray(),
        )
    }*/
    //?}

    fun syncGroupBlockEntities(
        world: ServerLevel,
        pos: BlockPos,
        state: FluidStoragePersistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE),
    ) {
        val storage = state.getStorage(pos) ?: return
        val groupId = state.getGroupId(pos)
        val shares = state.calculateGroupShares(pos, world)
        val groupPositions = state.getGroupPositions(pos)
        for (groupPos in groupPositions) {
            val blockEntity = world.getBlockEntity(groupPos) as? ConnectedTankBlockEntity ?: continue
            blockEntity.updateFromStorage(storage, shares[groupPos] ?: 0L, groupId)
        }
        updateConnectionStates(world, groupPositions, state)
    }

    private fun updateConnectionStates(
        world: ServerLevel,
        positions: Iterable<BlockPos>,
        state: FluidStoragePersistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE),
    ) {
        val positionsToUpdate = mutableSetOf<BlockPos>()
        for (pos in positions) {
            positionsToUpdate.add(pos)
            for (offset in FluidStoragePersistentState.ADJACENT_OFFSETS) {
                val neighborPos = pos.offset(offset)
                if (isConnectedTank(world.getBlockState(neighborPos).block)) {
                    positionsToUpdate.add(neighborPos)
                }
            }
        }
        for (targetPos in positionsToUpdate) {
            val currentState = world.getBlockState(targetPos)
            if (currentState.block !is ConnectedTankBlock) continue
            val myGroupId = state.getGroupId(targetPos)
            var newState = currentState
            for ((direction, property) in ConnectedTankBlock.DIRECTION_PROPERTIES) {
                val neighborPos = targetPos.relative(direction)
                val neighborBlock = world.getBlockState(neighborPos).block
                val connected = if (isConnectedTank(neighborBlock) && myGroupId != null) {
                    state.getGroupId(neighborPos) == myGroupId
                } else {
                    false
                }
                newState = newState.setValue(property, connected)
            }
            if (newState != currentState) {
                world.setBlock(targetPos, newState, Block.UPDATE_CLIENTS)
            }
        }
    }
}
