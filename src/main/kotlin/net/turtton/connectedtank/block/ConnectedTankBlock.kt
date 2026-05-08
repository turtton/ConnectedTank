package net.turtton.connectedtank.block

import java.util.concurrent.ConcurrentHashMap
//? if fabric {
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes
import net.fabricmc.loader.api.FabricLoader
//?} else if neoforge {
/*import net.neoforged.fml.loading.FMLLoader
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.transfer.fluid.FluidUtil*/
//?}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.fluid.FLUID_BUCKET
import net.turtton.connectedtank.world.FluidStoragePersistentState

class ConnectedTankBlock(val tier: TankTier, settings: Properties) :
    Block(settings),
    EntityBlock {
    companion object {
        val CONNECTED_NORTH: BooleanProperty = BooleanProperty.create("connected_north")
        val CONNECTED_SOUTH: BooleanProperty = BooleanProperty.create("connected_south")
        val CONNECTED_EAST: BooleanProperty = BooleanProperty.create("connected_east")
        val CONNECTED_WEST: BooleanProperty = BooleanProperty.create("connected_west")
        val CONNECTED_UP: BooleanProperty = BooleanProperty.create("connected_up")
        val CONNECTED_DOWN: BooleanProperty = BooleanProperty.create("connected_down")

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
        registerDefaultState(
            stateDefinition.any()
                .setValue(CONNECTED_NORTH, false)
                .setValue(CONNECTED_SOUTH, false)
                .setValue(CONNECTED_EAST, false)
                .setValue(CONNECTED_WEST, false)
                .setValue(CONNECTED_UP, false)
                .setValue(CONNECTED_DOWN, false),
        )
    }

    private val pendingDropData = ConcurrentHashMap<BlockPos, TankFluidStorage.ExistingData>()

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(CONNECTED_NORTH, CONNECTED_SOUTH, CONNECTED_EAST, CONNECTED_WEST, CONNECTED_UP, CONNECTED_DOWN)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        // 設置直後は BlockEntity がまだ存在しないため、全方向 false で設置する。
        // 正しい接続状態は onPlaced → syncGroupBlockEntities で確定する。
        return defaultBlockState()
    }

    override fun updateShape(
        state: BlockState,
        world: LevelReader,
        tickView: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource,
    ): BlockState {
        val property = DIRECTION_PROPERTIES[direction] ?: return state
        if (!CTBlocks.isConnectedTank(neighborState.block)) {
            return state.setValue(property, false)
        }
        return state
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ConnectedTankBlockEntity(pos, state)

    override fun skipRendering(state: BlockState, stateFrom: BlockState, direction: Direction): Boolean {
        val property = DIRECTION_PROPERTIES[direction] ?: return super.skipRendering(state, stateFrom, direction)
        return state.getValue(property) || super.skipRendering(state, stateFrom, direction)
    }

    override fun propagatesSkylightDown(state: BlockState): Boolean = true

    override fun getShadeBrightness(state: BlockState, world: BlockGetter, pos: BlockPos): Float = 1.0F

    override fun affectNeighborsAfterRemoval(state: BlockState, world: ServerLevel, pos: BlockPos, moved: Boolean) {
        val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
        val neighborPositions = FluidStoragePersistentState.ADJACENT_OFFSETS
            .map { pos.offset(it) }
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

        super.affectNeighborsAfterRemoval(state, world, pos, moved)
    }

    public override fun getCloneItemStack(world: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean): ItemStack {
        val stack = super.getCloneItemStack(world, pos, state, includeData)
        if (!includeData || world !is ServerLevel) return stack
        val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
        val fluidData = computeFluidData(persistentState, pos, world) ?: return stack
        stack.set(CTDataComponentTypes.TANK_FLUID, fluidData)
        return stack
    }

    override fun getDrops(state: BlockState, builder: LootParams.Builder): List<ItemStack> {
        val stack = ItemStack(this)
        val origin = builder.getParameter(LootContextParams.ORIGIN)
        val pos = BlockPos.containing(origin)

        val fluidData = pendingDropData.remove(pos)
            ?: run {
                // Explosion パス: ストレージがまだ存在する
                val world = builder.level
                val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
                computeFluidData(persistentState, pos, world)?.also {
                    pendingDropData[pos] = it
                }
            }

        if (fluidData != null) {
            stack.set(CTDataComponentTypes.TANK_FLUID, fluidData)
        }
        return listOf(stack)
    }

    private fun computeFluidData(
        persistentState: FluidStoragePersistentState,
        pos: BlockPos,
        world: ServerLevel,
    ): TankFluidStorage.ExistingData? {
        val tankStorage = persistentState.getStorage(pos)
        if (tankStorage == null || tankStorage.isResourceBlank) return null
        val share = persistentState.calculateShare(pos, world, tier.bucketCapacity)
        if (share <= 0) return null
        return TankFluidStorage.ExistingData(tankStorage.variant, share)
    }

    override fun setPlacedBy(world: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        if (world is ServerLevel) {
            val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
            val fluidData = itemStack.get(CTDataComponentTypes.TANK_FLUID)
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

    override fun useWithoutItem(state: BlockState, world: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        //? if fabric {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) return InteractionResult.PASS
        //?} else if neoforge {
        /*if (FMLLoader.getCurrent().isProduction) return InteractionResult.PASS*/
        //?}
        if (world !is ServerLevel) return InteractionResult.SUCCESS

        val storage = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
        val tankStorage = storage.getStorage(pos)
        if (tankStorage == null) {
            //? if >=26.1 {
            /*player.sendOverlayMessage(Component.literal("No storage"))*/
            //?} else {
            player.displayClientMessage(Component.literal("No storage"), true)
            //?}
            return InteractionResult.SUCCESS
        }

        val fluidName = if (tankStorage.isResourceBlank) {
            "Empty"
        } else {
            //? if fabric {
            FluidVariantAttributes.getName(tankStorage.variant).string
            //?} else if neoforge {
            /*tankStorage.variant.hoverName.string*/
            //?}
        }
        val buckets = tankStorage.amount.toDouble() / FLUID_BUCKET
        val capacity = tankStorage.bucketCapacity
        //? if >=26.1 {
        /*player.sendOverlayMessage(Component.literal("$fluidName: %.2f / %d buckets".format(buckets, capacity)))*/
        //?} else {
        player.displayClientMessage(Component.literal("$fluidName: %.2f / %d buckets".format(buckets, capacity)), true)
        //?}
        return InteractionResult.SUCCESS
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, world: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        if (world !is ServerLevel) return InteractionResult.SUCCESS

        val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
        val tankStorage = persistentState.getStorage(pos) ?: return InteractionResult.TRY_WITH_EMPTY_HAND
        //? if fabric {
        val result = FluidStorageUtil.interactWithFluidStorage(tankStorage, player, hand)
        return if (result) {
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
            InteractionResult.SUCCESS
        } else {
            InteractionResult.TRY_WITH_EMPTY_HAND
        }
        //?} else if neoforge {
        /*val handler = world.getCapability(Capabilities.Fluid.BLOCK, pos, hit.direction) ?: tankStorage
        val actionResult = FluidUtil.interactWithFluidHandler(player, hand, pos, handler)
        return if (actionResult) {
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
            InteractionResult.SUCCESS
        } else {
            InteractionResult.TRY_WITH_EMPTY_HAND
        }*/
        //?}
    }
}
