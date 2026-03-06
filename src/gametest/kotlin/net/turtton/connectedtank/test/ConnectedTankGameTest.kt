package net.turtton.connectedtank.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.block.Blocks
import net.minecraft.fluid.Fluids
import net.minecraft.item.ItemStack
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.GameMode
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.item.CTItems
import net.turtton.connectedtank.world.FluidStoragePersistentState

object ConnectedTankGameTest {
    private fun TestContext.getFluidState(): FluidStoragePersistentState = getWorld().persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)

    /**
     * useStackOnBlock で指定位置にタンクを設置する。
     * 足場として 1 つ下にブロックを置き、その UP 面をクリックする。
     * @param tankPos タンクを置きたい相対座標 (y >= 2)
     */
    private fun TestContext.placeTank(tankPos: BlockPos) {
        val basePos = tankPos.down()
        setBlockState(basePos, Blocks.STONE)
        val player = createMockPlayer(GameMode.SURVIVAL)
        val stack = ItemStack(CTItems.CONNECTED_TANK)
        // useStackOnBlock は pos.offset(direction) のブロック面をクリックする
        // basePos をクリックするには pos.offset(UP) = basePos → pos = basePos.down()
        useStackOnBlock(player, stack, basePos.down(), Direction.UP)
    }

    @GameTest
    fun placeSingleTank(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val storage = state.getStorage(context.getAbsolutePos(tankPos))
        context.assertTrue(storage != null, Text.literal("Storage should exist after placing tank"))
        context.assertTrue(storage!!.amount == 0L, Text.literal("New tank should be empty"))
        context.assertTrue(
            storage.bucketCapacity == 32,
            Text.literal("Single tank capacity should be 32 buckets"),
        )
        context.complete()
    }

    @GameTest
    fun placeAdjacentTanksShareStorage(context: TestContext) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.getAbsolutePos(pos1))
        val storage2 = state.getStorage(context.getAbsolutePos(pos2))
        context.assertTrue(storage1 != null, Text.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Text.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 === storage2,
            Text.literal("Adjacent tanks should share the same storage instance"),
        )
        context.assertTrue(
            storage1!!.bucketCapacity == 64,
            Text.literal("Combined tank capacity should be 64 buckets"),
        )
        context.complete()
    }

    @GameTest
    fun removeTankFromCombinedReducesCapacity(context: TestContext) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        state.removeStorage(context.getAbsolutePos(pos2))

        val remaining = state.getStorage(context.getAbsolutePos(pos1))
        context.assertTrue(remaining != null, Text.literal("Remaining storage should exist"))
        context.assertTrue(
            remaining!!.bucketCapacity == 32,
            Text.literal("Capacity should be reduced to 32 buckets after removing one tank"),
        )

        val removed = state.getStorage(context.getAbsolutePos(pos2))
        context.assertTrue(removed == null, Text.literal("Removed position should have no storage"))
        context.complete()
    }

    @GameTest
    fun fluidInsertionPersists(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val storage = state.getStorage(context.getAbsolutePos(tankPos))!!

        val water = FluidVariant.of(Fluids.WATER)
        Transaction.openOuter().use { transaction ->
            val inserted = storage.insert(water, FluidConstants.BUCKET, transaction)
            context.assertTrue(
                inserted == FluidConstants.BUCKET,
                Text.literal("Should insert exactly 1 bucket"),
            )
            transaction.commit()
        }

        context.assertTrue(storage.amount == FluidConstants.BUCKET, Text.literal("Storage should contain 1 bucket"))
        context.assertTrue(storage.variant == water, Text.literal("Storage should contain water"))
        context.complete()
    }

    @GameTest
    fun disconnectedTanksHaveSeparateStorage(context: TestContext) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(2, 2, 0) // 1 block gap
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.getAbsolutePos(pos1))
        val storage2 = state.getStorage(context.getAbsolutePos(pos2))
        context.assertTrue(storage1 != null, Text.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Text.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 !== storage2,
            Text.literal("Non-adjacent tanks should have separate storage"),
        )
        context.complete()
    }

    @GameTest
    fun removeAllTanksRemovesStorage(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val absPos = context.getAbsolutePos(tankPos)
        context.assertTrue(state.getStorage(absPos) != null, Text.literal("Storage should exist"))

        state.removeStorage(absPos)
        context.assertTrue(state.getStorage(absPos) == null, Text.literal("Storage should be removed"))
        context.complete()
    }

    @GameTest
    fun placesBetweenTwoGroupsMergesThem(context: TestContext) {
        // [Group A] [gap] [Group B] → [Group A] [New Tank] [Group B] → 1 group
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)

        val state = context.getFluidState()
        val storageA = state.getStorage(context.getAbsolutePos(posA))
        val storageB = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(storageA !== storageB, Text.literal("Groups should be separate before merge"))

        context.placeTank(posMid)

        val sA = state.getStorage(context.getAbsolutePos(posA))
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sB = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(sA != null, Text.literal("Storage A should exist"))
        context.assertTrue(sA === sMid, Text.literal("A and Mid should share storage"))
        context.assertTrue(sA === sB, Text.literal("A and B should share storage after merge"))
        context.assertTrue(
            sA!!.bucketCapacity == 96,
            Text.literal("Merged capacity should be 96 buckets but was ${sA.bucketCapacity}"),
        )
        context.complete()
    }

    @GameTest
    fun mergeGroupsPreservesFluidAmount(context: TestContext) {
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)

        // 両グループに水を入れる
        val storageA = state.getStorage(context.getAbsolutePos(posA))!!
        Transaction.openOuter().use { transaction ->
            storageA.insert(water, FluidConstants.BUCKET * 2, transaction)
            transaction.commit()
        }
        val storageB = state.getStorage(context.getAbsolutePos(posB))!!
        Transaction.openOuter().use { transaction ->
            storageB.insert(water, FluidConstants.BUCKET * 3, transaction)
            transaction.commit()
        }

        // 間にタンクを置いてマージ
        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posMid)

        val merged = state.getStorage(context.getAbsolutePos(posA))!!
        context.assertTrue(
            merged.amount == FluidConstants.BUCKET * 5,
            Text.literal("Merged amount should be 5 buckets but was ${merged.amount / FluidConstants.BUCKET}"),
        )
        context.assertTrue(merged.variant == water, Text.literal("Merged variant should be water"))
        context.complete()
    }

    @GameTest
    fun incompatibleGroupsDoNotMerge(context: TestContext) {
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val lava = FluidVariant.of(Fluids.LAVA)

        // A に水を入れる
        val storageA = state.getStorage(context.getAbsolutePos(posA))!!
        Transaction.openOuter().use { transaction ->
            storageA.insert(water, FluidConstants.BUCKET, transaction)
            transaction.commit()
        }

        // B にラバを直接追加
        val lavaStorage = TankFluidStorage(
            32,
            TankFluidStorage.ExistingData(lava, FluidConstants.BUCKET),
        )
        state.addStorage(context.getAbsolutePos(posB), lavaStorage)

        // 間にタンクを置く → マージ不可、独立グループになるべき
        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posMid)

        val sA = state.getStorage(context.getAbsolutePos(posA))
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sB = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(sA !== sMid, Text.literal("Water group should not merge with middle"))
        context.assertTrue(sB !== sMid, Text.literal("Lava group should not merge with middle"))
        context.assertTrue(sA!!.variant == water, Text.literal("A should still have water"))
        context.assertTrue(sB!!.variant == lava, Text.literal("B should still have lava"))
        context.complete()
    }

    @GameTest
    fun differentFluidTanksDoNotMerge(context: TestContext) {
        val pos1 = BlockPos(0, 2, 0)
        context.placeTank(pos1)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.getAbsolutePos(pos1))!!

        val water = FluidVariant.of(Fluids.WATER)
        Transaction.openOuter().use { transaction ->
            storage1.insert(water, FluidConstants.BUCKET, transaction)
            transaction.commit()
        }

        // 隣接位置にラバ入りタンクを直接追加 (addStorage のマージ拒否ロジックをテスト)
        val pos2 = BlockPos(1, 2, 0)
        val lavaStorage = TankFluidStorage(
            32,
            TankFluidStorage.ExistingData(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET),
        )
        state.addStorage(context.getAbsolutePos(pos2), lavaStorage)

        val s1 = state.getStorage(context.getAbsolutePos(pos1))
        val s2 = state.getStorage(context.getAbsolutePos(pos2))
        context.assertTrue(s1 !== s2, Text.literal("Tanks with different fluids should not merge"))
        context.assertTrue(s1!!.variant == water, Text.literal("First tank should still have water"))
        context.assertTrue(
            s2!!.variant == FluidVariant.of(Fluids.LAVA),
            Text.literal("Second tank should have lava"),
        )
        context.complete()
    }
}
