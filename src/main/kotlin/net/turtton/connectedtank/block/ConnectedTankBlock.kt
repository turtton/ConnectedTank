package net.turtton.connectedtank.block

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.turtton.connectedtank.world.FluidStoragePersistentState

class ConnectedTankBlock(settings: Settings) : Block(settings) {
    override fun onBreak(world: World?, pos: BlockPos?, state: BlockState?, player: PlayerEntity?): BlockState? {
        if (world is ServerWorld && pos != null) {
            val storage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            storage.removeStorage(pos)
        }
        return super.onBreak(world, pos, state, player)
    }

    override fun onPlaced(world: World?, pos: BlockPos?, state: BlockState?, placer: LivingEntity?, itemStack: ItemStack?) {
        if (world is ServerWorld && pos != null) {
            val storage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            storage.addStorage(pos, TankFluidStorage())
        }
    }

    override fun onUseWithItem(stack: ItemStack?, state: BlockState?, world: World?, pos: BlockPos?, player: PlayerEntity?, hand: Hand?, hit: BlockHitResult?): ActionResult? {
        if (world !is ServerWorld) return ActionResult.SUCCESS
        if (pos == null) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION

        val handContext = ContainerItemContext.ofPlayerHand(player, hand)
        val handStorage = handContext.find(FluidStorage.ITEM)
        if (handStorage != null) {
            val tankStorage = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE).getStorage(pos) ?: return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION

            val targetVariant = tankStorage.variant.takeIf { !it.isBlank } ?: handStorage.firstOrNull()?.resource ?: return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION
            Transaction.openOuter().use { transaction ->
                val extractedAmount = handStorage.extract(targetVariant, tankStorage.capacity - tankStorage.amount, transaction)
                tankStorage.insert(targetVariant, extractedAmount, transaction)
                transaction.commit()
            }
        }
        return ActionResult.SUCCESS
    }
}
