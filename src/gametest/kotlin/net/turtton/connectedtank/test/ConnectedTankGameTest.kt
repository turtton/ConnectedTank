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
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.block.TankTier
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.item.CTItems
import net.turtton.connectedtank.world.FluidStoragePersistentState

object ConnectedTankGameTest {
    private fun TestContext.getFluidState(): FluidStoragePersistentState = getWorld().persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)

    /**
     * useStackOnBlock で指定位置にタンクを設置する。
     * 足場として 1 つ下にブロックを置き、その UP 面をクリックする。
     * @param tankPos タンクを置きたい相対座標 (y >= 2)
     * @param tier 設置するタンクのティア (デフォルト BASE)
     */
    private fun TestContext.placeTank(tankPos: BlockPos, tier: TankTier = TankTier.BASE) {
        val basePos = tankPos.down()
        setBlockState(basePos, Blocks.STONE)
        val player = createMockPlayer(GameMode.SURVIVAL)
        val item = CTItems.ALL_TANK_ITEMS.first {
            (CTBlocks.ALL_TANKS[CTItems.ALL_TANK_ITEMS.indexOf(it)] as? ConnectedTankBlock)?.tier == tier
        }
        val stack = ItemStack(item)
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
            storage.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Text.literal("Single tank capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} buckets"),
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
            storage1!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 2,
            Text.literal("Combined tank capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 2} buckets"),
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
        state.removeStorage(context.getAbsolutePos(pos2), context.getWorld())

        val remaining = state.getStorage(context.getAbsolutePos(pos1))
        context.assertTrue(remaining != null, Text.literal("Remaining storage should exist"))
        context.assertTrue(
            remaining!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Text.literal("Capacity should be reduced to ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} buckets after removing one tank"),
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

        state.removeStorage(absPos, context.getWorld())
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
            sA!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3,
            Text.literal("Merged capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3} buckets but was ${sA.bucketCapacity}"),
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
    fun incompatibleGroupsConnectToPriority(context: TestContext) {
        // 水タンクと溶岩タンクの間に空タンクを置くと、座標優先度で水側に接続
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val lava = FluidVariant.of(Fluids.LAVA)

        val storageA = state.getStorage(context.getAbsolutePos(posA))!!
        Transaction.openOuter().use { transaction ->
            storageA.insert(water, FluidConstants.BUCKET, transaction)
            transaction.commit()
        }

        val lavaStorage = TankFluidStorage(
            CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            TankFluidStorage.ExistingData(lava, FluidConstants.BUCKET),
        )
        state.addStorage(context.getAbsolutePos(posB), lavaStorage)

        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posMid)

        val sA = state.getStorage(context.getAbsolutePos(posA))
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sB = state.getStorage(context.getAbsolutePos(posB))
        // 座標優先度: posA(0,2,0) < posB(2,2,0) → 空タンクは水グループに接続
        context.assertTrue(sA === sMid, Text.literal("Empty tank should connect to water group (higher priority)"))
        context.assertTrue(sB !== sMid, Text.literal("Lava group should remain separate"))
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

        val pos2 = BlockPos(1, 2, 0)
        val lavaStorage = TankFluidStorage(
            CTServerConfig.DEFAULT_BUCKET_CAPACITY,
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

    // === 座標優先度・interactedAt テスト ===

    @GameTest
    fun interactedAtConnectsToSpecifiedGroup(context: TestContext) {
        // 水グループと溶岩グループの間で、interactedAt で溶岩側を指定
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val lava = FluidVariant.of(Fluids.LAVA)

        val storageA = state.getStorage(context.getAbsolutePos(posA))!!
        Transaction.openOuter().use { transaction ->
            storageA.insert(water, FluidConstants.BUCKET, transaction)
            transaction.commit()
        }

        val lavaStorage = TankFluidStorage(
            CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            TankFluidStorage.ExistingData(lava, FluidConstants.BUCKET),
        )
        state.addStorage(context.getAbsolutePos(posB), lavaStorage)

        // interactedAt で溶岩側 (posB) を指定して addStorage
        val posMid = BlockPos(1, 2, 0)
        val midStorage = TankFluidStorage(CTServerConfig.DEFAULT_BUCKET_CAPACITY)
        state.addStorage(context.getAbsolutePos(posMid), midStorage, context.getAbsolutePos(posB))

        val sA = state.getStorage(context.getAbsolutePos(posA))
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sB = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(sB === sMid, Text.literal("Middle should connect to lava group via interactedAt"))
        context.assertTrue(sA !== sMid, Text.literal("Water group should remain separate"))
        context.assertTrue(sA!!.variant == water, Text.literal("A should still have water"))
        context.assertTrue(sB!!.variant == lava, Text.literal("B+Mid should have lava"))
        context.complete()
    }

    @GameTest
    fun interactedAtDoesNotMergeOtherGroups(context: TestContext) {
        // interactedAt 指定時、他の互換グループはマージしない
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)

        val state = context.getFluidState()
        val sA = state.getStorage(context.getAbsolutePos(posA))
        val sB = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(sA !== sB, Text.literal("Groups should be separate before placement"))

        // interactedAt で posB を指定 → posA のグループとはマージしない
        val posMid = BlockPos(1, 2, 0)
        val midStorage = TankFluidStorage(CTServerConfig.DEFAULT_BUCKET_CAPACITY)
        state.addStorage(context.getAbsolutePos(posMid), midStorage, context.getAbsolutePos(posB))

        val sA2 = state.getStorage(context.getAbsolutePos(posA))
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sB2 = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(sB2 === sMid, Text.literal("Mid should connect to B"))
        context.assertTrue(sA2 !== sMid, Text.literal("A should remain separate from Mid"))
        context.complete()
    }

    @GameTest
    fun coordinatePrioritySelectsLowestCoordinate(context: TestContext) {
        // Y が低い方が優先される
        val posBottom = BlockPos(1, 2, 0)
        val posTop = BlockPos(1, 4, 0)
        context.placeTank(posBottom)
        context.placeTank(posTop)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val lava = FluidVariant.of(Fluids.LAVA)

        val storageBottom = state.getStorage(context.getAbsolutePos(posBottom))!!
        Transaction.openOuter().use { tx ->
            storageBottom.insert(water, FluidConstants.BUCKET, tx)
            tx.commit()
        }
        val storageTop = state.getStorage(context.getAbsolutePos(posTop))!!
        Transaction.openOuter().use { tx ->
            storageTop.insert(lava, FluidConstants.BUCKET, tx)
            tx.commit()
        }

        // 中間に空タンクを設置 → Y 昇順で posBottom が優先
        val posMid = BlockPos(1, 3, 0)
        context.placeTank(posMid)

        val sBottom = state.getStorage(context.getAbsolutePos(posBottom))
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sTop = state.getStorage(context.getAbsolutePos(posTop))
        context.assertTrue(sBottom === sMid, Text.literal("Mid should connect to bottom (lower Y)"))
        context.assertTrue(sTop !== sMid, Text.literal("Top should remain separate"))
        context.complete()
    }

    // === 分断検出テスト ===

    @GameTest
    fun breakMiddleOfThreeSplitsIntoTwoGroups(context: TestContext) {
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val sAll = state.getStorage(context.getAbsolutePos(posL))
        context.assertTrue(
            sAll!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3,
            Text.literal("3 tanks should have ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3} bucket capacity"),
        )

        state.removeStorage(context.getAbsolutePos(posM), context.getWorld())

        val sL = state.getStorage(context.getAbsolutePos(posL))
        val sR = state.getStorage(context.getAbsolutePos(posR))
        context.assertTrue(sL != null, Text.literal("Left storage should exist"))
        context.assertTrue(sR != null, Text.literal("Right storage should exist"))
        context.assertTrue(sL !== sR, Text.literal("Left and right should be separate groups"))
        context.assertTrue(
            sL!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Text.literal("Left capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} but was ${sL.bucketCapacity}"),
        )
        context.assertTrue(
            sR!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Text.literal("Right capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} but was ${sR.bucketCapacity}"),
        )
        context.complete()
    }

    @GameTest
    fun breakCornerOfLShapeSplitsIntoTwo(context: TestContext) {
        // L 字: (0,2,0) - (1,2,0) - (1,2,1)
        val posA = BlockPos(0, 2, 0)
        val posCorner = BlockPos(1, 2, 0)
        val posB = BlockPos(1, 2, 1)
        context.placeTank(posA)
        context.placeTank(posCorner)
        context.placeTank(posB)

        val state = context.getFluidState()
        state.removeStorage(context.getAbsolutePos(posCorner), context.getWorld())

        val sA = state.getStorage(context.getAbsolutePos(posA))
        val sB = state.getStorage(context.getAbsolutePos(posB))
        context.assertTrue(sA != null, Text.literal("A should exist"))
        context.assertTrue(sB != null, Text.literal("B should exist"))
        context.assertTrue(sA !== sB, Text.literal("A and B should be separate after corner break"))
        context.complete()
    }

    @GameTest
    fun breakOneFrom2x2KeepsGroupConnected(context: TestContext) {
        // 2x2: (0,2,0) (1,2,0) (0,2,1) (1,2,1) → 1 つ破壊 → 残り 3 つは連結
        val pos00 = BlockPos(0, 2, 0)
        val pos10 = BlockPos(1, 2, 0)
        val pos01 = BlockPos(0, 2, 1)
        val pos11 = BlockPos(1, 2, 1)
        context.placeTank(pos00)
        context.placeTank(pos10)
        context.placeTank(pos01)
        context.placeTank(pos11)

        val state = context.getFluidState()
        state.removeStorage(context.getAbsolutePos(pos11), context.getWorld())

        val s00 = state.getStorage(context.getAbsolutePos(pos00))
        val s10 = state.getStorage(context.getAbsolutePos(pos10))
        val s01 = state.getStorage(context.getAbsolutePos(pos01))
        context.assertTrue(s00 != null, Text.literal("00 should exist"))
        context.assertTrue(s00 === s10, Text.literal("00 and 10 should share storage"))
        context.assertTrue(s00 === s01, Text.literal("00 and 01 should share storage"))
        context.assertTrue(
            s00!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3,
            Text.literal("Remaining 3 tanks should have ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3} bucket capacity but was ${s00.bucketCapacity}"),
        )
        context.complete()
    }

    // === 液体均等分配テスト ===

    @GameTest
    fun splitEvenFluidDistribution(context: TestContext) {
        // 30 バケツ / 3 タンク → 破壊タンク 10, 残り各 10
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage = state.getStorage(context.getAbsolutePos(posL))!!
        Transaction.openOuter().use { tx ->
            storage.insert(water, FluidConstants.BUCKET * 30, tx)
            tx.commit()
        }

        val removedData = state.removeStorage(context.getAbsolutePos(posM), context.getWorld())

        context.assertTrue(removedData != null, Text.literal("Removed data should not be null"))
        context.assertTrue(
            removedData!!.amount == FluidConstants.BUCKET * 10,
            Text.literal("Removed share should be 10 buckets but was ${removedData.amount / FluidConstants.BUCKET}"),
        )

        val sL = state.getStorage(context.getAbsolutePos(posL))
        val sR = state.getStorage(context.getAbsolutePos(posR))
        context.assertTrue(
            sL!!.amount == FluidConstants.BUCKET * 10,
            Text.literal("Left should have 10 buckets but was ${sL.amount / FluidConstants.BUCKET}"),
        )
        context.assertTrue(
            sR!!.amount == FluidConstants.BUCKET * 10,
            Text.literal("Right should have 10 buckets but was ${sR.amount / FluidConstants.BUCKET}"),
        )
        context.complete()
    }

    @GameTest
    fun splitUnevenFluidDistribution(context: TestContext) {
        // droplet 単位で端数が出るケース: (10 buckets + 2 droplets) / 3 tanks
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val totalAmount = FluidConstants.BUCKET * 10 + 2 // 810002 droplets
        val storage = state.getStorage(context.getAbsolutePos(posL))!!
        Transaction.openOuter().use { tx ->
            storage.insert(water, totalAmount, tx)
            tx.commit()
        }

        val removedData = state.removeStorage(context.getAbsolutePos(posM), context.getWorld())

        // 位置ベース分配: 同一 Y レベル・同一ティアなので累積丸めで按分
        // 810002 * 2/3 = 540001 (cumulative for posM) - 270000 (posL) = 270001
        val expectedRemoved = 270001L
        context.assertTrue(removedData != null, Text.literal("Removed data should not be null"))
        context.assertTrue(
            removedData!!.amount == expectedRemoved,
            Text.literal("Removed share should be $expectedRemoved but was ${removedData.amount}"),
        )

        // remaining = 810002 - 270001 = 540001, 2 tanks (同一 Y レベル)
        // 累積丸め: posL = 270000, posR = 270001 (または逆)
        val sL = state.getStorage(context.getAbsolutePos(posL))
        val sR = state.getStorage(context.getAbsolutePos(posR))
        val leftAmt = sL!!.amount
        val rightAmt = sR!!.amount
        context.assertTrue(
            leftAmt + rightAmt == 540001L,
            Text.literal("Total remaining should be 540001 but was ${leftAmt + rightAmt}"),
        )
        context.assertTrue(
            (leftAmt == 270001L && rightAmt == 270000L) || (leftAmt == 270000L && rightAmt == 270001L),
            Text.literal("Amounts should be 270001+270000 but were $leftAmt+$rightAmt"),
        )
        context.complete()
    }

    @GameTest
    fun noSplitFluidReduction(context: TestContext) {
        // 2 タンクから 1 つ破壊 (分断なし: 隣接なので分断にはならない)
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage = state.getStorage(context.getAbsolutePos(pos1))!!
        Transaction.openOuter().use { tx ->
            storage.insert(water, FluidConstants.BUCKET * 20, tx)
            tx.commit()
        }

        val removedData = state.removeStorage(context.getAbsolutePos(pos2), context.getWorld())

        context.assertTrue(removedData != null, Text.literal("Removed data should not be null"))
        context.assertTrue(
            removedData!!.amount == FluidConstants.BUCKET * 10,
            Text.literal("Removed share should be 10 buckets but was ${removedData.amount / FluidConstants.BUCKET}"),
        )

        val remaining = state.getStorage(context.getAbsolutePos(pos1))
        context.assertTrue(
            remaining!!.amount == FluidConstants.BUCKET * 10,
            Text.literal("Remaining should have 10 buckets but was ${remaining.amount / FluidConstants.BUCKET}"),
        )
        context.complete()
    }

    // === DataComponent テスト ===

    @GameTest
    fun removeStorageReturnsFluidData(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage = state.getStorage(context.getAbsolutePos(tankPos))!!
        Transaction.openOuter().use { tx ->
            storage.insert(water, FluidConstants.BUCKET * 5, tx)
            tx.commit()
        }

        val result = state.removeStorage(context.getAbsolutePos(tankPos), context.getWorld())
        context.assertTrue(result != null, Text.literal("Should return ExistingData"))
        context.assertTrue(result!!.variant == water, Text.literal("Variant should be water"))
        context.assertTrue(
            result.amount == FluidConstants.BUCKET * 5,
            Text.literal("Amount should be 5 buckets but was ${result.amount / FluidConstants.BUCKET}"),
        )
        context.complete()
    }

    @GameTest
    fun removeEmptyStorageReturnsNull(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val result = state.removeStorage(context.getAbsolutePos(tankPos), context.getWorld())
        context.assertTrue(result == null, Text.literal("Empty tank should return null"))
        context.complete()
    }

    @GameTest
    fun placeFluidTankRestoresStorage(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        val water = FluidVariant.of(Fluids.WATER)
        val fluidData = TankFluidStorage.ExistingData(water, FluidConstants.BUCKET * 5)

        // DataComponent 付きタンクを直接 addStorage で追加
        val state = context.getFluidState()
        val tankStorage = TankFluidStorage(fluid = fluidData)
        state.addStorage(context.getAbsolutePos(tankPos), tankStorage)

        val restored = state.getStorage(context.getAbsolutePos(tankPos))
        context.assertTrue(restored != null, Text.literal("Restored storage should exist"))
        context.assertTrue(restored!!.variant == water, Text.literal("Variant should be water"))
        context.assertTrue(
            restored.amount == FluidConstants.BUCKET * 5,
            Text.literal("Amount should be 5 buckets but was ${restored.amount / FluidConstants.BUCKET}"),
        )
        context.complete()
    }

    @GameTest
    fun placeFluidTankMergesWithAdjacent(context: TestContext) {
        // 隣に水タンクがある状態で、水入りタンクを設置 → 液体量がマージされる
        val pos1 = BlockPos(0, 2, 0)
        context.placeTank(pos1)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage1 = state.getStorage(context.getAbsolutePos(pos1))!!
        Transaction.openOuter().use { tx ->
            storage1.insert(water, FluidConstants.BUCKET * 3, tx)
            tx.commit()
        }

        // 水 2 バケツ入りタンクを隣に追加
        val pos2 = BlockPos(1, 2, 0)
        val fluidData = TankFluidStorage.ExistingData(water, FluidConstants.BUCKET * 2)
        val newTankStorage = TankFluidStorage(fluid = fluidData)
        state.addStorage(context.getAbsolutePos(pos2), newTankStorage)

        val merged = state.getStorage(context.getAbsolutePos(pos1))
        context.assertTrue(merged != null, Text.literal("Merged storage should exist"))
        context.assertTrue(
            merged!!.amount == FluidConstants.BUCKET * 5,
            Text.literal("Merged amount should be 5 buckets but was ${merged.amount / FluidConstants.BUCKET}"),
        )
        context.assertTrue(merged.variant == water, Text.literal("Variant should be water"))
        context.complete()
    }

    // === 垂直スタック位置ベース分配テスト ===

    private fun TestContext.placeVerticalTanks(vararg yPositions: Int, x: Int = 0, z: Int = 0): List<BlockPos> {
        val positions = yPositions.map { BlockPos(x, it, z) }
        val state = getFluidState()
        for (pos in positions) {
            setBlockState(pos, CTBlocks.CONNECTED_TANK.defaultState)
        }
        // 下から順に addStorage して接続
        for (pos in positions.sortedBy { it.y }) {
            val absPos = getAbsolutePos(pos)
            val storage = TankFluidStorage(CTServerConfig.DEFAULT_BUCKET_CAPACITY)
            state.addStorage(absPos, storage)
        }
        return positions
    }

    @GameTest
    fun verticalStackBottomGetsMoreFluid(context: TestContext) {
        // 3 段積み: 48 バケツ (50%) → 下=32, 中=16, 上=0
        val (posBottom, posMid, posTop) = context.placeVerticalTanks(2, 3, 4)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage = state.getStorage(context.getAbsolutePos(posBottom))!!
        val bucketCap = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
        val totalAmount = bucketCap * FluidConstants.BUCKET / 2 * 3 // 50% of total capacity
        Transaction.openOuter().use { tx ->
            storage.insert(water, totalAmount, tx)
            tx.commit()
        }

        // 中間タンクを破壊 → 位置ベースで分配
        val removedData = state.removeStorage(context.getAbsolutePos(posMid), context.getWorld())
        val expectedMid = (bucketCap / 2) * FluidConstants.BUCKET
        context.assertTrue(
            removedData != null,
            Text.literal("Removed data should not be null"),
        )
        context.assertTrue(
            removedData!!.amount == expectedMid,
            Text.literal("Mid share should be $expectedMid but was ${removedData.amount}"),
        )

        val sBottom = state.getStorage(context.getAbsolutePos(posBottom))
        val sTop = state.getStorage(context.getAbsolutePos(posTop))
        val expectedBottom = bucketCap * FluidConstants.BUCKET
        context.assertTrue(
            sBottom!!.amount == expectedBottom,
            Text.literal("Bottom should have $expectedBottom but was ${sBottom.amount}"),
        )
        context.assertTrue(
            sTop!!.amount == 0L,
            Text.literal("Top should have 0 but was ${sTop.amount}"),
        )
        context.complete()
    }

    @GameTest
    fun verticalStackBreakBottomRedistributes(context: TestContext) {
        // 3 段積み: 48 バケツ → 下を破壊
        // 下=32, 中=16, 上=0 → 下の 32 バケツがドロップ, 残り 16 バケツは中と上に再分配
        val (posBottom, posMid, posTop) = context.placeVerticalTanks(2, 3, 4)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage = state.getStorage(context.getAbsolutePos(posBottom))!!
        val bucketCap = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
        val totalAmount = bucketCap * FluidConstants.BUCKET / 2 * 3
        Transaction.openOuter().use { tx ->
            storage.insert(water, totalAmount, tx)
            tx.commit()
        }

        val removedData = state.removeStorage(context.getAbsolutePos(posBottom), context.getWorld())
        val expectedBottom = bucketCap * FluidConstants.BUCKET
        context.assertTrue(
            removedData != null,
            Text.literal("Removed data should not be null"),
        )
        context.assertTrue(
            removedData!!.amount == expectedBottom,
            Text.literal("Bottom share should be $expectedBottom but was ${removedData.amount}"),
        )

        // 残り 16 バケツ: 中と上は同一グループで共有ストレージ
        val sMid = state.getStorage(context.getAbsolutePos(posMid))
        val sTop = state.getStorage(context.getAbsolutePos(posTop))
        context.assertTrue(
            sMid === sTop,
            Text.literal("Mid and Top should share the same storage"),
        )
        val expectedRemaining = (bucketCap / 2) * FluidConstants.BUCKET
        context.assertTrue(
            sMid!!.amount == expectedRemaining,
            Text.literal("Remaining group should have $expectedRemaining but was ${sMid.amount}"),
        )
        context.complete()
    }

    @GameTest
    fun verticalStackEmptyTopGetsNothing(context: TestContext) {
        // 2 段積み: 容量の 30% → 下のみに入り、上を破壊しても液体なし
        val (posBottom, posTop) = context.placeVerticalTanks(2, 3)

        val state = context.getFluidState()
        val water = FluidVariant.of(Fluids.WATER)
        val storage = state.getStorage(context.getAbsolutePos(posBottom))!!
        val bucketCap = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
        val amount = bucketCap * FluidConstants.BUCKET * 30 / 100 // 30% of single tank
        Transaction.openOuter().use { tx ->
            storage.insert(water, amount, tx)
            tx.commit()
        }

        val removedData = state.removeStorage(context.getAbsolutePos(posTop), context.getWorld())
        context.assertTrue(
            removedData == null,
            Text.literal("Top tank should have no fluid to return"),
        )

        val sBottom = state.getStorage(context.getAbsolutePos(posBottom))
        context.assertTrue(
            sBottom!!.amount == amount,
            Text.literal("Bottom should retain all $amount but was ${sBottom.amount}"),
        )
        context.complete()
    }

    // === ティア別容量テスト ===

    @GameTest
    fun tierCapacityMatchesMultiplier(context: TestContext) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos, TankTier.IRON)

        val state = context.getFluidState()
        val storage = state.getStorage(context.getAbsolutePos(tankPos))
        val expectedCapacity = CTServerConfig.instance.getTierCapacity(TankTier.IRON)
        context.assertTrue(storage != null, Text.literal("Storage should exist"))
        context.assertTrue(
            storage!!.bucketCapacity == expectedCapacity,
            Text.literal("Iron tank capacity should be $expectedCapacity but was ${storage.bucketCapacity}"),
        )
        context.complete()
    }

    @GameTest
    fun differentTiersConnect(context: TestContext) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1, TankTier.BASE)
        context.placeTank(pos2, TankTier.IRON)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.getAbsolutePos(pos1))
        val storage2 = state.getStorage(context.getAbsolutePos(pos2))
        context.assertTrue(storage1 != null, Text.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Text.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 === storage2,
            Text.literal("Different tier tanks should share storage when adjacent"),
        )
        val expectedCapacity = CTServerConfig.instance.getTierCapacity(TankTier.BASE) +
            CTServerConfig.instance.getTierCapacity(TankTier.IRON)
        context.assertTrue(
            storage1!!.bucketCapacity == expectedCapacity,
            Text.literal("Combined capacity should be $expectedCapacity but was ${storage1.bucketCapacity}"),
        )
        context.complete()
    }

    @GameTest
    fun splitDifferentTiersRecalculatesCapacity(context: TestContext) {
        // BASE - IRON - BASE → IRON を破壊 → BASE 2 つに分断
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL, TankTier.BASE)
        context.placeTank(posM, TankTier.IRON)
        context.placeTank(posR, TankTier.BASE)

        val state = context.getFluidState()
        state.removeStorage(context.getAbsolutePos(posM), context.getWorld())

        val sL = state.getStorage(context.getAbsolutePos(posL))
        val sR = state.getStorage(context.getAbsolutePos(posR))
        context.assertTrue(sL != null, Text.literal("Left storage should exist"))
        context.assertTrue(sR != null, Text.literal("Right storage should exist"))
        context.assertTrue(sL !== sR, Text.literal("Should be separate groups"))
        val baseCap = CTServerConfig.instance.getTierCapacity(TankTier.BASE)
        context.assertTrue(
            sL!!.bucketCapacity == baseCap,
            Text.literal("Left capacity should be $baseCap but was ${sL.bucketCapacity}"),
        )
        context.assertTrue(
            sR!!.bucketCapacity == baseCap,
            Text.literal("Right capacity should be $baseCap but was ${sR.bucketCapacity}"),
        )
        context.complete()
    }
}
