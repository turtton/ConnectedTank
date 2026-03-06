package net.turtton.connectedtank.block

import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.world.FluidStoragePersistentState

class ConnectedTankBlock(settings: Settings) : Block(settings) {
    private val pendingDropData = ConcurrentHashMap<BlockPos, TankFluidStorage.ExistingData>()

    override fun onStateReplaced(state: BlockState, world: ServerWorld, pos: BlockPos, moved: Boolean) {
        val storage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
        if (!pendingDropData.containsKey(pos)) {
            // Player mining パス: getDroppedStacks がまだ呼ばれていない
            val fluidData = storage.removeStorage(pos)
            if (fluidData != null) pendingDropData[pos] = fluidData
        } else {
            // Explosion パス: getDroppedStacks が先に処理済み
            pendingDropData.remove(pos)
            storage.removeStorage(pos)
        }
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
                    val groupSize = persistentState.getGroupSize(pos)
                    val perTank = tankStorage.amount / groupSize
                    val share = perTank + (if (tankStorage.amount % groupSize > 0L) 1L else 0L)
                    TankFluidStorage.ExistingData(tankStorage.variant, share).also {
                        pendingDropData[pos] = it
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
            val storage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            val fluidData = itemStack?.get(CTDataComponentTypes.TANK_FLUID)
            val tankStorage = if (fluidData != null) {
                TankFluidStorage(fluid = fluidData)
            } else {
                TankFluidStorage()
            }
            storage.addStorage(pos, tankStorage)
        }
    }

    override fun onUseWithItem(stack: ItemStack?, state: BlockState?, world: World?, pos: BlockPos?, player: PlayerEntity?, hand: Hand?, hit: BlockHitResult?): ActionResult? {
        if (world !is ServerWorld) return ActionResult.SUCCESS
        if (pos == null) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION

        val tankStorage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE).getStorage(pos) ?: return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION
        val result = FluidStorageUtil.interactWithFluidStorage(tankStorage, player, hand)
        return if (result) {
            ActionResult.SUCCESS
        } else {
            ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION
        }
    }
}
