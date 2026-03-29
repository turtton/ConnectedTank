package net.turtton.connectedtank.block

import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.random.Random
import net.minecraft.world.World
import net.minecraft.world.WorldView
import net.minecraft.world.tick.ScheduledTickView
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.world.FluidStoragePersistentState

class ConnectedTankBlock(val tier: TankTier, settings: Settings) :
    Block(settings),
    BlockEntityProvider {
    companion object {
        val CONNECTED_NORTH: BooleanProperty = BooleanProperty.of("connected_north")
        val CONNECTED_SOUTH: BooleanProperty = BooleanProperty.of("connected_south")
        val CONNECTED_EAST: BooleanProperty = BooleanProperty.of("connected_east")
        val CONNECTED_WEST: BooleanProperty = BooleanProperty.of("connected_west")
        val CONNECTED_UP: BooleanProperty = BooleanProperty.of("connected_up")
        val CONNECTED_DOWN: BooleanProperty = BooleanProperty.of("connected_down")

        val DIRECTION_PROPERTIES: Map<Direction, BooleanProperty> = mapOf(
            Direction.NORTH to CONNECTED_NORTH,
            Direction.SOUTH to CONNECTED_SOUTH,
            Direction.EAST to CONNECTED_EAST,
            Direction.WEST to CONNECTED_WEST,
            Direction.UP to CONNECTED_UP,
            Direction.DOWN to CONNECTED_DOWN,
        )
    }

    init {
        defaultState = stateManager.defaultState
            .with(CONNECTED_NORTH, false)
            .with(CONNECTED_SOUTH, false)
            .with(CONNECTED_EAST, false)
            .with(CONNECTED_WEST, false)
            .with(CONNECTED_UP, false)
            .with(CONNECTED_DOWN, false)
    }

    private val pendingDropData = ConcurrentHashMap<BlockPos, TankFluidStorage.ExistingData>()

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(CONNECTED_NORTH, CONNECTED_SOUTH, CONNECTED_EAST, CONNECTED_WEST, CONNECTED_UP, CONNECTED_DOWN)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState {
        // 設置直後は BlockEntity がまだ存在しないため、全方向 false で設置する。
        // 正しい接続状態は onPlaced → syncGroupBlockEntities で確定する。
        return defaultState
    }

    override fun getStateForNeighborUpdate(
        state: BlockState,
        world: WorldView,
        tickView: ScheduledTickView,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: Random,
    ): BlockState {
        val property = DIRECTION_PROPERTIES[direction] ?: return state
        if (!CTBlocks.isConnectedTank(neighborState.block)) {
            return state.with(property, false)
        }
        return state
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ConnectedTankBlockEntity(pos, state)

    override fun isSideInvisible(state: BlockState, stateFrom: BlockState, direction: Direction): Boolean {
        val property = DIRECTION_PROPERTIES[direction] ?: return super.isSideInvisible(state, stateFrom, direction)
        return state.get(property) || super.isSideInvisible(state, stateFrom, direction)
    }

    override fun onStateReplaced(state: BlockState, world: ServerWorld, pos: BlockPos, moved: Boolean) {
        val persistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
        val neighborPositions = FluidStoragePersistentState.ADJACENT_OFFSETS
            .map { pos.add(it) }
            .filter { CTBlocks.isConnectedTank(world.getBlockState(it).block) }

        if (!pendingDropData.containsKey(pos)) {
            val fluidData = persistentState.removeStorage(pos, world, tier.bucketCapacity)
            if (fluidData != null) pendingDropData[pos] = fluidData
        } else {
            pendingDropData.remove(pos)
            persistentState.removeStorage(pos, world, tier.bucketCapacity)
        }

        for (neighborPos in neighborPositions) {
            CTBlocks.syncGroupBlockEntities(world, neighborPos, persistentState)
        }

        // クリエイティブモード等で getDroppedStacks が呼ばれないパスのクリーンアップ
        val immutablePos = pos.toImmutable()
        world.server?.execute { pendingDropData.remove(immutablePos) }

        super.onStateReplaced(state, world, pos, moved)
    }

    override fun getDroppedStacks(state: BlockState, builder: LootWorldContext.Builder): List<ItemStack> {
        val stack = ItemStack(this)
        val origin = builder.get(LootContextParameters.ORIGIN)
        val pos = BlockPos.ofFloored(origin)

        val fluidData = pendingDropData.remove(pos)
            ?: run {
                // Explosion パス: ストレージがまだ存在する
                val world = builder.world
                val persistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
                val tankStorage = persistentState.getStorage(pos)
                if (tankStorage != null && !tankStorage.isResourceBlank) {
                    val share = persistentState.calculateShare(pos, world, tier.bucketCapacity)
                    if (share > 0) {
                        TankFluidStorage.ExistingData(tankStorage.variant, share).also {
                            pendingDropData[pos] = it
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

        if (fluidData != null) {
            stack.set(CTDataComponentTypes.TANK_FLUID, fluidData)
        }
        return listOf(stack)
    }

    override fun onPlaced(world: World?, pos: BlockPos?, state: BlockState?, placer: LivingEntity?, itemStack: ItemStack?) {
        if (world is ServerWorld && pos != null) {
            val persistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            val fluidData = itemStack?.get(CTDataComponentTypes.TANK_FLUID)
            val block = world.getBlockState(pos).block as? ConnectedTankBlock
            val capacity = block?.tier?.bucketCapacity ?: CTServerConfig.instance.tankBucketCapacity
            val tankStorage = TankFluidStorage(capacity, fluidData)
            val interactedAt = ConnectedTankPlacementContext.consumeInteractedAt()?.takeIf {
                CTBlocks.isConnectedTank(world.getBlockState(it).block)
            }
            persistentState.addStorage(pos, tankStorage, interactedAt)
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
        }
    }

    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hit: BlockHitResult): ActionResult {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) return ActionResult.PASS
        if (world !is ServerWorld) return ActionResult.SUCCESS

        val storage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
        val tankStorage = storage.getStorage(pos)
        if (tankStorage == null) {
            player.sendMessage(Text.literal("No storage"), true)
            return ActionResult.SUCCESS
        }

        val fluidName = if (tankStorage.isResourceBlank) "Empty" else FluidVariantAttributes.getName(tankStorage.variant).string
        val buckets = tankStorage.amount.toDouble() / FluidConstants.BUCKET
        val capacity = tankStorage.bucketCapacity
        player.sendMessage(Text.literal("$fluidName: %.2f / %d buckets".format(buckets, capacity)), true)
        return ActionResult.SUCCESS
    }

    override fun onUseWithItem(stack: ItemStack?, state: BlockState?, world: World?, pos: BlockPos?, player: PlayerEntity?, hand: Hand?, hit: BlockHitResult?): ActionResult? {
        if (world !is ServerWorld) return ActionResult.SUCCESS
        if (pos == null) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION

        val persistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
        val tankStorage = persistentState.getStorage(pos) ?: return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION
        val result = FluidStorageUtil.interactWithFluidStorage(tankStorage, player, hand)
        return if (result) {
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
            ActionResult.SUCCESS
        } else {
            ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION
        }
    }
}
