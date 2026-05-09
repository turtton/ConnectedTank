package net.turtton.connectedtank.test

//? if fabric {
import net.fabricmc.fabric.api.gametest.v1.GameTest
//?}
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.item.ItemStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.AABB
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.block.TankTier
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.fluid.FLUID_BUCKET
import net.turtton.connectedtank.fluid.fluidVariantOf
import net.turtton.connectedtank.fluid.insertFluid
import net.turtton.connectedtank.fluid.isSameFluid
import net.turtton.connectedtank.item.CTItems
import net.turtton.connectedtank.world.FluidStoragePersistentState

object ConnectedTankGameTest {
    private fun GameTestHelper.getFluidState(): FluidStoragePersistentState = level.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)

    /**
     * useStackOnBlock で指定位置にタンクを設置する。
     * 足場として 1 つ下にブロックを置き、その UP 面をクリックする。
     * @param tankPos タンクを置きたい相対座標 (y >= 2)
     * @param tier 設置するタンクのティア (デフォルト BASE)
     */
    private fun GameTestHelper.placeTank(tankPos: BlockPos, tier: TankTier = TankTier.BASE, sneaking: Boolean = false) {
        val basePos = tankPos.below()
        setBlock(basePos, Blocks.STONE)
        val player = makeMockPlayer(GameType.SURVIVAL)
        val item = CTItems.ALL_TANK_ITEMS.first {
            (CTBlocks.ALL_TANKS[CTItems.ALL_TANK_ITEMS.indexOf(it)] as? ConnectedTankBlock)?.tier == tier
        }
        val stack = ItemStack(item)
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack)
        if (sneaking) player.setShiftKeyDown(true)
        placeAt(player, stack, basePos.below(), Direction.UP)
    }

    //? if fabric {
    @GameTest
    //?}
    fun placeSingleTank(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val storage = state.getStorage(context.absolutePos(tankPos))
        context.assertTrue(storage != null, Component.literal("Storage should exist after placing tank"))
        context.assertTrue(storage!!.amount == 0L, Component.literal("New tank should be empty"))
        context.assertTrue(
            storage.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Component.literal("Single tank capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} buckets"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun placeAdjacentTanksShareStorage(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.absolutePos(pos1))
        val storage2 = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(storage1 != null, Component.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Component.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 === storage2,
            Component.literal("Adjacent tanks should share the same storage instance"),
        )
        context.assertTrue(
            storage1!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 2,
            Component.literal("Combined tank capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 2} buckets"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun removeTankFromCombinedReducesCapacity(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        state.removeStorage(context.absolutePos(pos2), context.level)

        val remaining = state.getStorage(context.absolutePos(pos1))
        context.assertTrue(remaining != null, Component.literal("Remaining storage should exist"))
        context.assertTrue(
            remaining!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Component.literal("Capacity should be reduced to ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} buckets after removing one tank"),
        )

        val removed = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(removed == null, Component.literal("Removed position should have no storage"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun fluidInsertionPersists(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val storage = state.getStorage(context.absolutePos(tankPos))!!

        val water = fluidVariantOf(Fluids.WATER)
        insertFluid(storage, water, FLUID_BUCKET)

        context.assertTrue(storage.amount == FLUID_BUCKET, Component.literal("Storage should contain 1 bucket"))
        context.assertTrue(storage.variant.isSameFluid(water), Component.literal("Storage should contain water"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun disconnectedTanksHaveSeparateStorage(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(2, 2, 0) // 1 block gap
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.absolutePos(pos1))
        val storage2 = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(storage1 != null, Component.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Component.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 !== storage2,
            Component.literal("Non-adjacent tanks should have separate storage"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun removeAllTanksRemovesStorage(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val absPos = context.absolutePos(tankPos)
        context.assertTrue(state.getStorage(absPos) != null, Component.literal("Storage should exist"))

        state.removeStorage(absPos, context.level)
        context.assertTrue(state.getStorage(absPos) == null, Component.literal("Storage should be removed"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun placesBetweenTwoGroupsMergesThem(context: GameTestHelper) {
        // [Group A] [gap] [Group B] → [Group A] [New Tank] [Group B] → 1 group
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)

        val state = context.getFluidState()
        val storageA = state.getStorage(context.absolutePos(posA))
        val storageB = state.getStorage(context.absolutePos(posB))
        context.assertTrue(storageA !== storageB, Component.literal("Groups should be separate before merge"))

        context.placeTank(posMid)

        val sA = state.getStorage(context.absolutePos(posA))
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sB = state.getStorage(context.absolutePos(posB))
        context.assertTrue(sA != null, Component.literal("Storage A should exist"))
        context.assertTrue(sA === sMid, Component.literal("A and Mid should share storage"))
        context.assertTrue(sA === sB, Component.literal("A and B should share storage after merge"))
        context.assertTrue(
            sA!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3,
            Component.literal("Merged capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3} buckets but was ${sA.bucketCapacity}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun mergeGroupsPreservesFluidAmount(context: GameTestHelper) {
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)

        val storageA = state.getStorage(context.absolutePos(posA))!!
        insertFluid(storageA, water, FLUID_BUCKET * 2)
        val storageB = state.getStorage(context.absolutePos(posB))!!
        insertFluid(storageB, water, FLUID_BUCKET * 3)

        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posMid)

        val merged = state.getStorage(context.absolutePos(posA))!!
        context.assertTrue(
            merged.amount == FLUID_BUCKET * 5,
            Component.literal("Merged amount should be 5 buckets but was ${merged.amount / FLUID_BUCKET}"),
        )
        context.assertTrue(merged.variant.isSameFluid(water), Component.literal("Merged variant should be water"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun incompatibleGroupsConnectToPriority(context: GameTestHelper) {
        // 水タンクと溶岩タンクの間に空タンクを置くと、座標優先度で水側に接続
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val lava = fluidVariantOf(Fluids.LAVA)

        val storageA = state.getStorage(context.absolutePos(posA))!!
        insertFluid(storageA, water, FLUID_BUCKET)

        val lavaStorage = TankFluidStorage(
            CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            TankFluidStorage.ExistingData(lava, FLUID_BUCKET),
        )
        state.addStorage(context.absolutePos(posB), lavaStorage)

        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posMid)

        val sA = state.getStorage(context.absolutePos(posA))
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sB = state.getStorage(context.absolutePos(posB))
        // 座標優先度: posA(0,2,0) < posB(2,2,0) → 空タンクは水グループに接続
        context.assertTrue(sA === sMid, Component.literal("Empty tank should connect to water group (higher priority)"))
        context.assertTrue(sB !== sMid, Component.literal("Lava group should remain separate"))
        context.assertTrue(sA!!.variant.isSameFluid(water), Component.literal("A should still have water"))
        context.assertTrue(sB!!.variant.isSameFluid(lava), Component.literal("B should still have lava"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun differentFluidTanksDoNotMerge(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        context.placeTank(pos1)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.absolutePos(pos1))!!

        val water = fluidVariantOf(Fluids.WATER)
        insertFluid(storage1, water, FLUID_BUCKET)

        val pos2 = BlockPos(1, 2, 0)
        val lavaStorage = TankFluidStorage(
            CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            TankFluidStorage.ExistingData(fluidVariantOf(Fluids.LAVA), FLUID_BUCKET),
        )
        state.addStorage(context.absolutePos(pos2), lavaStorage)

        val s1 = state.getStorage(context.absolutePos(pos1))
        val s2 = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(s1 !== s2, Component.literal("Tanks with different fluids should not merge"))
        context.assertTrue(s1!!.variant.isSameFluid(water), Component.literal("First tank should still have water"))
        context.assertTrue(
            s2!!.variant.isSameFluid(fluidVariantOf(Fluids.LAVA)),
            Component.literal("Second tank should have lava"),
        )
        context.succeed()
    }

    // === 座標優先度・interactedAt テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun interactedAtConnectsToSpecifiedGroup(context: GameTestHelper) {
        // 水グループと溶岩グループの間で、interactedAt で溶岩側を指定
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val lava = fluidVariantOf(Fluids.LAVA)

        val storageA = state.getStorage(context.absolutePos(posA))!!
        insertFluid(storageA, water, FLUID_BUCKET)

        val lavaStorage = TankFluidStorage(
            CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            TankFluidStorage.ExistingData(lava, FLUID_BUCKET),
        )
        state.addStorage(context.absolutePos(posB), lavaStorage)

        // interactedAt で溶岩側 (posB) を指定して addStorage
        val posMid = BlockPos(1, 2, 0)
        val midStorage = TankFluidStorage(CTServerConfig.DEFAULT_BUCKET_CAPACITY)
        state.addStorage(context.absolutePos(posMid), midStorage, context.absolutePos(posB))

        val sA = state.getStorage(context.absolutePos(posA))
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sB = state.getStorage(context.absolutePos(posB))
        context.assertTrue(sB === sMid, Component.literal("Middle should connect to lava group via interactedAt"))
        context.assertTrue(sA !== sMid, Component.literal("Water group should remain separate"))
        context.assertTrue(sA!!.variant.isSameFluid(water), Component.literal("A should still have water"))
        context.assertTrue(sB!!.variant.isSameFluid(lava), Component.literal("B+Mid should have lava"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun interactedAtDoesNotMergeOtherGroups(context: GameTestHelper) {
        // interactedAt 指定時、他の互換グループはマージしない
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)

        val state = context.getFluidState()
        val sA = state.getStorage(context.absolutePos(posA))
        val sB = state.getStorage(context.absolutePos(posB))
        context.assertTrue(sA !== sB, Component.literal("Groups should be separate before placement"))

        // interactedAt で posB を指定 → posA のグループとはマージしない
        val posMid = BlockPos(1, 2, 0)
        val midStorage = TankFluidStorage(CTServerConfig.DEFAULT_BUCKET_CAPACITY)
        state.addStorage(context.absolutePos(posMid), midStorage, context.absolutePos(posB))

        val sA2 = state.getStorage(context.absolutePos(posA))
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sB2 = state.getStorage(context.absolutePos(posB))
        context.assertTrue(sB2 === sMid, Component.literal("Mid should connect to B"))
        context.assertTrue(sA2 !== sMid, Component.literal("A should remain separate from Mid"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun coordinatePrioritySelectsLowestCoordinate(context: GameTestHelper) {
        // Y が低い方が優先される
        val posBottom = BlockPos(1, 2, 0)
        val posTop = BlockPos(1, 4, 0)
        context.placeTank(posBottom)
        context.placeTank(posTop)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val lava = fluidVariantOf(Fluids.LAVA)

        val storageBottom = state.getStorage(context.absolutePos(posBottom))!!
        insertFluid(storageBottom, water, FLUID_BUCKET)
        val storageTop = state.getStorage(context.absolutePos(posTop))!!
        insertFluid(storageTop, lava, FLUID_BUCKET)

        // 中間に空タンクを設置 → Y 昇順で posBottom が優先
        val posMid = BlockPos(1, 3, 0)
        context.placeTank(posMid)

        val sBottom = state.getStorage(context.absolutePos(posBottom))
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sTop = state.getStorage(context.absolutePos(posTop))
        context.assertTrue(sBottom === sMid, Component.literal("Mid should connect to bottom (lower Y)"))
        context.assertTrue(sTop !== sMid, Component.literal("Top should remain separate"))
        context.succeed()
    }

    // === 分断検出テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun breakMiddleOfThreeSplitsIntoTwoGroups(context: GameTestHelper) {
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val sAll = state.getStorage(context.absolutePos(posL))
        context.assertTrue(
            sAll!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3,
            Component.literal("3 tanks should have ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3} bucket capacity"),
        )

        state.removeStorage(context.absolutePos(posM), context.level)

        val sL = state.getStorage(context.absolutePos(posL))
        val sR = state.getStorage(context.absolutePos(posR))
        context.assertTrue(sL != null, Component.literal("Left storage should exist"))
        context.assertTrue(sR != null, Component.literal("Right storage should exist"))
        context.assertTrue(sL !== sR, Component.literal("Left and right should be separate groups"))
        context.assertTrue(
            sL!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Component.literal("Left capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} but was ${sL.bucketCapacity}"),
        )
        context.assertTrue(
            sR!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Component.literal("Right capacity should be ${CTServerConfig.DEFAULT_BUCKET_CAPACITY} but was ${sR.bucketCapacity}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun breakCornerOfLShapeSplitsIntoTwo(context: GameTestHelper) {
        // L 字: (0,2,0) - (1,2,0) - (1,2,1)
        val posA = BlockPos(0, 2, 0)
        val posCorner = BlockPos(1, 2, 0)
        val posB = BlockPos(1, 2, 1)
        context.placeTank(posA)
        context.placeTank(posCorner)
        context.placeTank(posB)

        val state = context.getFluidState()
        state.removeStorage(context.absolutePos(posCorner), context.level)

        val sA = state.getStorage(context.absolutePos(posA))
        val sB = state.getStorage(context.absolutePos(posB))
        context.assertTrue(sA != null, Component.literal("A should exist"))
        context.assertTrue(sB != null, Component.literal("B should exist"))
        context.assertTrue(sA !== sB, Component.literal("A and B should be separate after corner break"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun breakOneFrom2x2KeepsGroupConnected(context: GameTestHelper) {
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
        state.removeStorage(context.absolutePos(pos11), context.level)

        val s00 = state.getStorage(context.absolutePos(pos00))
        val s10 = state.getStorage(context.absolutePos(pos10))
        val s01 = state.getStorage(context.absolutePos(pos01))
        context.assertTrue(s00 != null, Component.literal("00 should exist"))
        context.assertTrue(s00 === s10, Component.literal("00 and 10 should share storage"))
        context.assertTrue(s00 === s01, Component.literal("00 and 01 should share storage"))
        context.assertTrue(
            s00!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3,
            Component.literal("Remaining 3 tanks should have ${CTServerConfig.DEFAULT_BUCKET_CAPACITY * 3} bucket capacity but was ${s00.bucketCapacity}"),
        )
        context.succeed()
    }

    // === 液体均等分配テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun splitEvenFluidDistribution(context: GameTestHelper) {
        // 30 バケツ / 3 タンク → 破壊タンク 10, 残り各 10
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(posL))!!
        insertFluid(storage, water, FLUID_BUCKET * 30)

        val removedData = state.removeStorage(context.absolutePos(posM), context.level)

        context.assertTrue(removedData != null, Component.literal("Removed data should not be null"))
        context.assertTrue(
            removedData!!.amount == FLUID_BUCKET * 10,
            Component.literal("Removed share should be 10 buckets but was ${removedData.amount / FLUID_BUCKET}"),
        )

        val sL = state.getStorage(context.absolutePos(posL))
        val sR = state.getStorage(context.absolutePos(posR))
        context.assertTrue(
            sL!!.amount == FLUID_BUCKET * 10,
            Component.literal("Left should have 10 buckets but was ${sL.amount / FLUID_BUCKET}"),
        )
        context.assertTrue(
            sR!!.amount == FLUID_BUCKET * 10,
            Component.literal("Right should have 10 buckets but was ${sR.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun splitUnevenFluidDistribution(context: GameTestHelper) {
        // droplet 単位で端数が出るケース: (10 buckets + 2 droplets) / 3 tanks
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val totalAmount = FLUID_BUCKET * 10 + 2
        val storage = state.getStorage(context.absolutePos(posL))!!
        insertFluid(storage, water, totalAmount)

        val removedData = state.removeStorage(context.absolutePos(posM), context.level)

        // 位置ベース分配: 同一 Y レベル・同一ティアなので累積丸めで按分
        val expectedPosLCum = totalAmount * 1 / 3
        val expectedPosMCum = totalAmount * 2 / 3
        val expectedRemoved = expectedPosMCum - expectedPosLCum
        context.assertTrue(removedData != null, Component.literal("Removed data should not be null"))
        context.assertTrue(
            removedData!!.amount == expectedRemoved,
            Component.literal("Removed share should be $expectedRemoved but was ${removedData.amount}"),
        )

        val expectedRemaining = totalAmount - expectedRemoved
        val sL = state.getStorage(context.absolutePos(posL))
        val sR = state.getStorage(context.absolutePos(posR))
        val leftAmt = sL!!.amount
        val rightAmt = sR!!.amount
        context.assertTrue(
            leftAmt + rightAmt == expectedRemaining,
            Component.literal("Total remaining should be $expectedRemaining but was ${leftAmt + rightAmt}"),
        )
        val expectedMin = expectedRemaining / 2
        val expectedMax = expectedRemaining - expectedMin
        context.assertTrue(
            (leftAmt == expectedMax && rightAmt == expectedMin) || (leftAmt == expectedMin && rightAmt == expectedMax),
            Component.literal("Amounts should be $expectedMax+$expectedMin but were $leftAmt+$rightAmt"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun noSplitFluidReduction(context: GameTestHelper) {
        // 2 タンクから 1 つ破壊 (分断なし: 隣接なので分断にはならない)
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(pos1))!!
        insertFluid(storage, water, FLUID_BUCKET * 20)

        val removedData = state.removeStorage(context.absolutePos(pos2), context.level)

        context.assertTrue(removedData != null, Component.literal("Removed data should not be null"))
        context.assertTrue(
            removedData!!.amount == FLUID_BUCKET * 10,
            Component.literal("Removed share should be 10 buckets but was ${removedData.amount / FLUID_BUCKET}"),
        )

        val remaining = state.getStorage(context.absolutePos(pos1))
        context.assertTrue(
            remaining!!.amount == FLUID_BUCKET * 10,
            Component.literal("Remaining should have 10 buckets but was ${remaining.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    // === DataComponent テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun removeStorageReturnsFluidData(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 5)

        val result = state.removeStorage(context.absolutePos(tankPos), context.level)
        context.assertTrue(result != null, Component.literal("Should return ExistingData"))
        context.assertTrue(result!!.variant.isSameFluid(water), Component.literal("Variant should be water"))
        context.assertTrue(
            result.amount == FLUID_BUCKET * 5,
            Component.literal("Amount should be 5 buckets but was ${result.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun removeEmptyStorageReturnsNull(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val result = state.removeStorage(context.absolutePos(tankPos), context.level)
        context.assertTrue(result == null, Component.literal("Empty tank should return null"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun placeFluidTankRestoresStorage(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        val water = fluidVariantOf(Fluids.WATER)
        val fluidData = TankFluidStorage.ExistingData(water, FLUID_BUCKET * 5)

        // DataComponent 付きタンクを直接 addStorage で追加
        val state = context.getFluidState()
        val tankStorage = TankFluidStorage(fluid = fluidData)
        state.addStorage(context.absolutePos(tankPos), tankStorage)

        val restored = state.getStorage(context.absolutePos(tankPos))
        context.assertTrue(restored != null, Component.literal("Restored storage should exist"))
        context.assertTrue(restored!!.variant.isSameFluid(water), Component.literal("Variant should be water"))
        context.assertTrue(
            restored.amount == FLUID_BUCKET * 5,
            Component.literal("Amount should be 5 buckets but was ${restored.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun placeFluidTankMergesWithAdjacent(context: GameTestHelper) {
        // 隣に水タンクがある状態で、水入りタンクを設置 → 液体量がマージされる
        val pos1 = BlockPos(0, 2, 0)
        context.placeTank(pos1)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage1 = state.getStorage(context.absolutePos(pos1))!!
        insertFluid(storage1, water, FLUID_BUCKET * 3)

        // 水 2 バケツ入りタンクを隣に追加
        val pos2 = BlockPos(1, 2, 0)
        val fluidData = TankFluidStorage.ExistingData(water, FLUID_BUCKET * 2)
        val newTankStorage = TankFluidStorage(fluid = fluidData)
        state.addStorage(context.absolutePos(pos2), newTankStorage)

        val merged = state.getStorage(context.absolutePos(pos1))
        context.assertTrue(merged != null, Component.literal("Merged storage should exist"))
        context.assertTrue(
            merged!!.amount == FLUID_BUCKET * 5,
            Component.literal("Merged amount should be 5 buckets but was ${merged.amount / FLUID_BUCKET}"),
        )
        context.assertTrue(merged.variant.isSameFluid(water), Component.literal("Variant should be water"))
        context.succeed()
    }

    // === 垂直スタック位置ベース分配テスト ===

    private fun GameTestHelper.placeVerticalTanks(vararg yPositions: Int, x: Int = 0, z: Int = 0): List<BlockPos> {
        val positions = yPositions.map { BlockPos(x, it, z) }
        val state = getFluidState()
        for (pos in positions) {
            setBlock(pos, CTBlocks.CONNECTED_TANK.defaultBlockState())
        }
        // 下から順に addStorage して接続
        for (pos in positions.sortedBy { it.y }) {
            val absPos = absolutePos(pos)
            val storage = TankFluidStorage(CTServerConfig.DEFAULT_BUCKET_CAPACITY)
            state.addStorage(absPos, storage)
        }
        return positions
    }

    //? if fabric {
    @GameTest
    //?}
    fun verticalStackBottomGetsMoreFluid(context: GameTestHelper) {
        // 3 段積み: 48 バケツ (50%) → 下=32, 中=16, 上=0
        val (posBottom, posMid, posTop) = context.placeVerticalTanks(2, 3, 4)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(posBottom))!!
        val bucketCap = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
        val totalAmount = bucketCap * FLUID_BUCKET / 2 * 3 // 50% of total capacity
        insertFluid(storage, water, totalAmount)

        // 中間タンクを破壊 → 位置ベースで分配
        val removedData = state.removeStorage(context.absolutePos(posMid), context.level)
        val expectedMid = (bucketCap / 2) * FLUID_BUCKET
        context.assertTrue(
            removedData != null,
            Component.literal("Removed data should not be null"),
        )
        context.assertTrue(
            removedData!!.amount == expectedMid,
            Component.literal("Mid share should be $expectedMid but was ${removedData.amount}"),
        )

        val sBottom = state.getStorage(context.absolutePos(posBottom))
        val sTop = state.getStorage(context.absolutePos(posTop))
        val expectedBottom = bucketCap * FLUID_BUCKET
        context.assertTrue(
            sBottom!!.amount == expectedBottom,
            Component.literal("Bottom should have $expectedBottom but was ${sBottom.amount}"),
        )
        context.assertTrue(
            sTop!!.amount == 0L,
            Component.literal("Top should have 0 but was ${sTop.amount}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun verticalStackBreakBottomRedistributes(context: GameTestHelper) {
        // 3 段積み: 48 バケツ → 下を破壊
        // 下=32, 中=16, 上=0 → 下の 32 バケツがドロップ, 残り 16 バケツは中と上に再分配
        val (posBottom, posMid, posTop) = context.placeVerticalTanks(2, 3, 4)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(posBottom))!!
        val bucketCap = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
        val totalAmount = bucketCap * FLUID_BUCKET / 2 * 3
        insertFluid(storage, water, totalAmount)

        val removedData = state.removeStorage(context.absolutePos(posBottom), context.level)
        val expectedBottom = bucketCap * FLUID_BUCKET
        context.assertTrue(
            removedData != null,
            Component.literal("Removed data should not be null"),
        )
        context.assertTrue(
            removedData!!.amount == expectedBottom,
            Component.literal("Bottom share should be $expectedBottom but was ${removedData.amount}"),
        )

        // 残り 16 バケツ: 中と上は同一グループで共有ストレージ
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sTop = state.getStorage(context.absolutePos(posTop))
        context.assertTrue(
            sMid === sTop,
            Component.literal("Mid and Top should share the same storage"),
        )
        val expectedRemaining = (bucketCap / 2) * FLUID_BUCKET
        context.assertTrue(
            sMid!!.amount == expectedRemaining,
            Component.literal("Remaining group should have $expectedRemaining but was ${sMid.amount}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun verticalStackEmptyTopGetsNothing(context: GameTestHelper) {
        // 2 段積み: 容量の 30% → 下のみに入り、上を破壊しても液体なし
        val (posBottom, posTop) = context.placeVerticalTanks(2, 3)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(posBottom))!!
        val bucketCap = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
        val amount = bucketCap * FLUID_BUCKET * 30 / 100 // 30% of single tank
        insertFluid(storage, water, amount)

        val removedData = state.removeStorage(context.absolutePos(posTop), context.level)
        context.assertTrue(
            removedData == null,
            Component.literal("Top tank should have no fluid to return"),
        )

        val sBottom = state.getStorage(context.absolutePos(posBottom))
        context.assertTrue(
            sBottom!!.amount == amount,
            Component.literal("Bottom should retain all $amount but was ${sBottom.amount}"),
        )
        context.succeed()
    }

    // === ティア別容量テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun tierCapacityMatchesMultiplier(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos, TankTier.IRON)

        val state = context.getFluidState()
        val storage = state.getStorage(context.absolutePos(tankPos))
        val expectedCapacity = CTServerConfig.instance.getTierCapacity(TankTier.IRON)
        context.assertTrue(storage != null, Component.literal("Storage should exist"))
        context.assertTrue(
            storage!!.bucketCapacity == expectedCapacity,
            Component.literal("Iron tank capacity should be $expectedCapacity but was ${storage.bucketCapacity}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun differentTiersConnect(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1, TankTier.BASE)
        context.placeTank(pos2, TankTier.IRON)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.absolutePos(pos1))
        val storage2 = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(storage1 != null, Component.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Component.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 === storage2,
            Component.literal("Different tier tanks should share storage when adjacent"),
        )
        val expectedCapacity = CTServerConfig.instance.getTierCapacity(TankTier.BASE) +
            CTServerConfig.instance.getTierCapacity(TankTier.IRON)
        context.assertTrue(
            storage1!!.bucketCapacity == expectedCapacity,
            Component.literal("Combined capacity should be $expectedCapacity but was ${storage1.bucketCapacity}"),
        )
        context.succeed()
    }

    // === getPickStack テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun pickStackWithIncludeDataContainsFluid(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 5)

        val world = context.level
        val absPos = context.absolutePos(tankPos)
        val blockState = world.getBlockState(absPos)
        val block = blockState.block as ConnectedTankBlock
        val stack = block.getCloneItemStack(world, absPos, blockState, true)

        val fluidData = stack.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData != null, Component.literal("Pick stack should have fluid data"))
        context.assertTrue(fluidData!!.variant.isSameFluid(water), Component.literal("Variant should be water"))
        context.assertTrue(
            fluidData.amount == FLUID_BUCKET * 5,
            Component.literal("Should have 5 buckets but was ${fluidData.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun pickStackWithoutIncludeDataHasNoFluid(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 5)

        val world = context.level
        val absPos = context.absolutePos(tankPos)
        val blockState = world.getBlockState(absPos)
        val block = blockState.block as ConnectedTankBlock
        val stack = block.getCloneItemStack(world, absPos, blockState, false)

        val fluidData = stack.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData == null, Component.literal("Pick stack without includeData should have no fluid data"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun pickStackFromEmptyTankHasNoFluidData(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val world = context.level
        val absPos = context.absolutePos(tankPos)
        val blockState = world.getBlockState(absPos)
        val block = blockState.block as ConnectedTankBlock
        val stack = block.getCloneItemStack(world, absPos, blockState, true)

        val fluidData = stack.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData == null, Component.literal("Empty tank pick stack should have no fluid data"))
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun pickStackFromConnectedTanksCalculatesShare(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        val pos3 = BlockPos(2, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2)
        context.placeTank(pos3)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(pos1))!!
        insertFluid(storage, water, FLUID_BUCKET * 30)

        val world = context.level
        val absPos2 = context.absolutePos(pos2)
        val blockState = world.getBlockState(absPos2)
        val block = blockState.block as ConnectedTankBlock
        val stack = block.getCloneItemStack(world, absPos2, blockState, true)

        val fluidData = stack.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData != null, Component.literal("Pick stack should have fluid data"))
        context.assertTrue(fluidData!!.variant.isSameFluid(water), Component.literal("Variant should be water"))
        context.assertTrue(
            fluidData.amount == FLUID_BUCKET * 10,
            Component.literal("Share should be 10 buckets but was ${fluidData.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun splitDifferentTiersRecalculatesCapacity(context: GameTestHelper) {
        // BASE - IRON - BASE → IRON を破壊 → BASE 2 つに分断
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL, TankTier.BASE)
        context.placeTank(posM, TankTier.IRON)
        context.placeTank(posR, TankTier.BASE)

        val state = context.getFluidState()
        state.removeStorage(context.absolutePos(posM), context.level)

        val sL = state.getStorage(context.absolutePos(posL))
        val sR = state.getStorage(context.absolutePos(posR))
        context.assertTrue(sL != null, Component.literal("Left storage should exist"))
        context.assertTrue(sR != null, Component.literal("Right storage should exist"))
        context.assertTrue(sL !== sR, Component.literal("Should be separate groups"))
        val baseCap = CTServerConfig.instance.getTierCapacity(TankTier.BASE)
        context.assertTrue(
            sL!!.bucketCapacity == baseCap,
            Component.literal("Left capacity should be $baseCap but was ${sL.bucketCapacity}"),
        )
        context.assertTrue(
            sR!!.bucketCapacity == baseCap,
            Component.literal("Right capacity should be $baseCap but was ${sR.bucketCapacity}"),
        )
        context.succeed()
    }

    // === ブロック破壊時の液体保持テスト ===

    private fun GameTestHelper.findDroppedTankItem(relativePos: BlockPos): ItemStack? {
        val absPos = absolutePos(relativePos)
        val entities = level.getEntities(EntityType.ITEM, AABB(absPos).inflate(1.0)) { it.isAlive }
        for (entity in entities) {
            val itemEntity = entity as ItemEntity
            val stack = itemEntity.item
            if (stack.item in CTItems.ALL_TANK_ITEMS) return stack
        }
        return null
    }

    //? if fabric {
    @GameTest
    //?}
    fun breakSingleTankRetainsFluidInDrop(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 5)

        // ブロック破壊 (ドロップ生成あり)
        context.level.destroyBlock(context.absolutePos(tankPos), true)

        // ドロップされたアイテムを検索
        val droppedStack = context.findDroppedTankItem(tankPos)
        context.assertTrue(droppedStack != null, Component.literal("Dropped tank item should exist"))

        val fluidData = droppedStack!!.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData != null, Component.literal("Dropped item should have fluid data"))
        context.assertTrue(fluidData!!.variant.isSameFluid(water), Component.literal("Fluid variant should be water"))
        context.assertTrue(
            fluidData.amount == FLUID_BUCKET * 5,
            Component.literal("Fluid amount should be 5 buckets but was ${fluidData.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun breakTankAndReplaceRestoresFluid(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 7)

        // ブロック破壊
        context.level.destroyBlock(context.absolutePos(tankPos), true)

        // ストレージが削除されたことを確認
        val removedStorage = state.getStorage(context.absolutePos(tankPos))
        context.assertTrue(removedStorage == null, Component.literal("Storage should be removed after breaking"))

        // ドロップされたアイテムから液体データを取得
        val droppedStack = context.findDroppedTankItem(tankPos)
        context.assertTrue(droppedStack != null, Component.literal("Dropped tank item should exist"))
        val fluidData = droppedStack!!.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData != null, Component.literal("Dropped item should have fluid data"))

        // 液体入りタンクを再設置 (setPlacedBy をシミュレート)
        val block = CTBlocks.CONNECTED_TANK as ConnectedTankBlock
        context.setBlock(tankPos, block.defaultBlockState())
        val capacity = block.tier.bucketCapacity
        val tankStorage = TankFluidStorage(capacity, fluidData)
        state.addStorage(context.absolutePos(tankPos), tankStorage)

        // 液体が復元されたことを確認
        val restored = state.getStorage(context.absolutePos(tankPos))
        context.assertTrue(restored != null, Component.literal("Restored storage should exist"))
        context.assertTrue(restored!!.variant.isSameFluid(water), Component.literal("Restored variant should be water"))
        context.assertTrue(
            restored.amount == FLUID_BUCKET * 7,
            Component.literal("Restored amount should be 7 buckets but was ${restored.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun breakMiddleTankRetainsShareInDrop(context: GameTestHelper) {
        // 3 連結タンク (各 32 バケツ容量) に 30 バケツ注入 → 中央を破壊 → 中央のシェア (10 バケツ) がドロップ
        val posL = BlockPos(0, 2, 0)
        val posM = BlockPos(1, 2, 0)
        val posR = BlockPos(2, 2, 0)
        context.placeTank(posL)
        context.placeTank(posM)
        context.placeTank(posR)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(posL))!!
        insertFluid(storage, water, FLUID_BUCKET * 30)

        // 中央タンクを破壊
        context.level.destroyBlock(context.absolutePos(posM), true)

        // ドロップされたアイテムのシェアを確認
        val droppedStack = context.findDroppedTankItem(posM)
        context.assertTrue(droppedStack != null, Component.literal("Dropped tank item should exist"))

        val fluidData = droppedStack!!.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData != null, Component.literal("Dropped item should have fluid data"))
        context.assertTrue(
            fluidData!!.amount == FLUID_BUCKET * 10,
            Component.literal("Middle share should be 10 buckets but was ${fluidData.amount / FLUID_BUCKET}"),
        )

        // 残りのタンクは分断されてそれぞれ 10 バケツ
        val sL = state.getStorage(context.absolutePos(posL))
        val sR = state.getStorage(context.absolutePos(posR))
        context.assertTrue(sL !== sR, Component.literal("Left and right should be separate groups"))
        context.assertTrue(
            sL!!.amount == FLUID_BUCKET * 10,
            Component.literal("Left should have 10 buckets but was ${sL.amount / FLUID_BUCKET}"),
        )
        context.assertTrue(
            sR!!.amount == FLUID_BUCKET * 10,
            Component.literal("Right should have 10 buckets but was ${sR.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun destroyBlockDoesNotLeakStaleFluidData(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 5)

        context.level.destroyBlock(context.absolutePos(tankPos), true)

        val absPos = context.absolutePos(tankPos)
        context.level.getEntities(EntityType.ITEM, AABB(absPos).inflate(1.0)) { true }
            .forEach { it.discard() }

        context.placeTank(tankPos)
        context.level.destroyBlock(context.absolutePos(tankPos), true)

        val droppedStack = context.findDroppedTankItem(tankPos)
        context.assertTrue(droppedStack != null, Component.literal("Empty tank should drop"))

        val fluidData = droppedStack!!.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(
            fluidData == null,
            Component.literal("Empty tank should not have stale fluid data"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun survivalMiningRetainsFluid(context: GameTestHelper) {
        val tankPos = BlockPos(0, 2, 0)
        context.placeTank(tankPos)

        val state = context.getFluidState()
        val water = fluidVariantOf(Fluids.WATER)
        val storage = state.getStorage(context.absolutePos(tankPos))!!
        insertFluid(storage, water, FLUID_BUCKET * 5)

        val player = context.makeMockServerPlayerInLevel()
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL)
        player.gameMode.destroyBlock(context.absolutePos(tankPos))

        val droppedStack = context.findDroppedTankItem(tankPos)
        context.assertTrue(droppedStack != null, Component.literal("Dropped tank item should exist"))

        val fluidData = droppedStack!!.get(CTDataComponentTypes.TANK_FLUID)
        context.assertTrue(fluidData != null, Component.literal("Survival mining should retain fluid data"))
        context.assertTrue(fluidData!!.variant.isSameFluid(water), Component.literal("Fluid variant should be water"))
        context.assertTrue(
            fluidData.amount == FLUID_BUCKET * 5,
            Component.literal("Fluid amount should be 5 buckets but was ${fluidData.amount / FLUID_BUCKET}"),
        )
        context.succeed()
    }

    // === スニーク設置分離テスト ===

    //? if fabric {
    @GameTest
    //?}
    fun sneakingPlacementOnNonTankCreatesIsolatedGroup(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2, sneaking = true)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.absolutePos(pos1))
        val storage2 = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(storage1 != null, Component.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Component.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 !== storage2,
            Component.literal("Sneaking placement should create isolated group"),
        )
        context.assertTrue(
            storage1!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Component.literal("First tank should have single capacity"),
        )
        context.assertTrue(
            storage2!!.bucketCapacity == CTServerConfig.DEFAULT_BUCKET_CAPACITY,
            Component.literal("Second tank should have single capacity"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun normalPlacementNextToTankConnects(context: GameTestHelper) {
        val pos1 = BlockPos(0, 2, 0)
        val pos2 = BlockPos(1, 2, 0)
        context.placeTank(pos1)
        context.placeTank(pos2, sneaking = false)

        val state = context.getFluidState()
        val storage1 = state.getStorage(context.absolutePos(pos1))
        val storage2 = state.getStorage(context.absolutePos(pos2))
        context.assertTrue(storage1 != null, Component.literal("Storage1 should exist"))
        context.assertTrue(storage2 != null, Component.literal("Storage2 should exist"))
        context.assertTrue(
            storage1 === storage2,
            Component.literal("Normal placement should connect to adjacent tank"),
        )
        context.succeed()
    }

    //? if fabric {
    @GameTest
    //?}
    fun sneakingPlacementBetweenTwoGroupsStaysIsolated(context: GameTestHelper) {
        val posA = BlockPos(0, 2, 0)
        val posB = BlockPos(2, 2, 0)
        val posMid = BlockPos(1, 2, 0)
        context.placeTank(posA)
        context.placeTank(posB)
        context.placeTank(posMid, sneaking = true)

        val state = context.getFluidState()
        val sA = state.getStorage(context.absolutePos(posA))
        val sMid = state.getStorage(context.absolutePos(posMid))
        val sB = state.getStorage(context.absolutePos(posB))
        context.assertTrue(sA !== sMid, Component.literal("Sneaking mid should not connect to A"))
        context.assertTrue(sB !== sMid, Component.literal("Sneaking mid should not connect to B"))
        context.assertTrue(sA !== sB, Component.literal("A and B should remain separate"))
        context.succeed()
    }
}
