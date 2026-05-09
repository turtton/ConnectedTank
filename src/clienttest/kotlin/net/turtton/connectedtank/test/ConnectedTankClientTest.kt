package net.turtton.connectedtank.test

import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.Fluids
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.block.TankTier
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.fluid.FLUID_BUCKET
import net.turtton.connectedtank.fluid.fluidVariantOf
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object ConnectedTankClientTest {
    private val logger = LoggerFactory.getLogger("ConnectedTankClientTest")
    private val TANK_CAPACITY = CTServerConfig.DEFAULT_BUCKET_CAPACITY.toLong()
    private val basePos = BlockPos(0, -60, 0)

    suspend fun runAll(context: ClientTestContext) {
        context.runCommand("gamemode spectator @p")
        context.waitTicks(5)

        // Jade ツールチップの確認は HUD 表示状態が必要なため、F1 (HUD 非表示) の前に実行
        testJadeFluidTooltip(context)

        context.pressKey(GLFW.GLFW_KEY_F1)
        context.waitTicks(5)

        testEmptyTank(context)
        testFullWaterTank(context)
        testHalfWaterTank(context)
        testHorizontalConnectedTanks(context)
        testVerticalConnectedTanks(context)
        testVerticalPartialFillTopFace(context)
        testVerticalDifferentFluidsStacked(context)
        testVerticalSameFluidDifferentGroups(context)
        testItemInventory(context)
    }

    private suspend fun takeQualityScreenshots(
        context: ClientTestContext,
        baseName: String,
    ) {
        for (quality in CTClientConfig.RenderQuality.entries) {
            CTClientConfig.instance.renderQuality = quality
            context.waitTicks(5)
            context.takeScreenshot("${baseName}_${quality.name.lowercase()}")
        }
    }

    private suspend fun testEmptyTank(context: ClientTestContext) {
        logger.info("Running: testEmptyTank")
        context.clearArea(basePos, 3, 3, 3)
        context.placeTank(basePos)
        context.waitTicks(20)
        context.setupCamera(1.8, -58.5, 1.8, 135f, 50f)
        context.takeScreenshot("1_empty_tank")
    }

    private suspend fun testFullWaterTank(context: ClientTestContext) {
        logger.info("Running: testFullWaterTank")
        context.clearArea(basePos, 3, 3, 3)
        context.placeTank(basePos)
        context.insertFluidAt(basePos, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY)
        context.waitTicks(20)
        context.setupCamera(1.8, -58.5, 1.8, 135f, 50f)
        takeQualityScreenshots(context, "2_full_water_tank")
    }

    private suspend fun testHalfWaterTank(context: ClientTestContext) {
        logger.info("Running: testHalfWaterTank")
        context.clearArea(basePos, 3, 3, 3)
        context.placeTank(basePos)
        context.insertFluidAt(basePos, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY / 2)
        context.waitTicks(20)
        context.setupCamera(1.8, -58.5, 1.8, 135f, 50f)
        takeQualityScreenshots(context, "3_half_water_tank")
    }

    private suspend fun testHorizontalConnectedTanks(context: ClientTestContext) {
        logger.info("Running: testHorizontalConnectedTanks")
        context.clearArea(basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.east()
        context.placeTank(pos1)
        context.placeTank(pos2)
        context.insertFluidAt(pos1, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY * 2)
        context.waitTicks(20)
        context.setupCamera(2.5, -58.5, 2.5, 135f, 45f)
        takeQualityScreenshots(context, "4_horizontal_connected_tanks")
    }

    private suspend fun testVerticalConnectedTanks(context: ClientTestContext) {
        logger.info("Running: testVerticalConnectedTanks")
        context.clearArea(basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.above()
        context.placeTank(pos1)
        context.placeTank(pos2)
        context.insertFluidAt(pos1, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY * 2)
        context.waitTicks(20)
        context.setupCamera(1.8, -57.0, 1.8, 135f, 45f)
        takeQualityScreenshots(context, "5_vertical_connected_tanks")
    }

    private suspend fun testVerticalPartialFillTopFace(context: ClientTestContext) {
        logger.info("Running: testVerticalPartialFillTopFace")
        // 同一液体で下のタンクのみに液体がある場合、上面が正しく描画されることを確認
        context.clearArea(basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.above()
        context.placeTank(pos1)
        context.placeTank(pos2)
        // 下タンクの半分だけ液体を入れる（上タンクには液体なし）
        context.insertFluidAt(pos1, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY / 2)
        context.waitTicks(20)
        context.setupCamera(1.8, -57.0, 1.8, 135f, 45f)
        takeQualityScreenshots(context, "7_vertical_partial_fill_top_face")
    }

    private suspend fun testVerticalDifferentFluidsStacked(context: ClientTestContext) {
        logger.info("Running: testVerticalDifferentFluidsStacked")
        // 異なる液体のタンクを縦に積んだ場合、両方の液体が描画されることを確認
        context.clearArea(basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.above()
        context.placeTank(pos1)
        context.insertFluidAt(pos1, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY)
        context.placeIsolatedTank(
            pos2,
            TankFluidStorage.ExistingData(fluidVariantOf(Fluids.LAVA), FLUID_BUCKET * TANK_CAPACITY),
        )
        context.waitTicks(20)
        context.setupCamera(1.8, -57.0, 1.8, 135f, 45f)
        takeQualityScreenshots(context, "8_vertical_different_fluids_stacked")
    }

    private suspend fun testVerticalSameFluidDifferentGroups(context: ClientTestContext) {
        logger.info("Running: testVerticalSameFluidDifferentGroups")
        // 同一液体・別グループのタンクが隣接している場合、境界面が描画されることを確認
        context.clearArea(basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.above()
        context.placeTank(pos1)
        context.insertFluidAt(pos1, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY)
        context.placeIsolatedTank(
            pos2,
            TankFluidStorage.ExistingData(fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY),
        )
        context.waitTicks(20)
        context.setupCamera(1.8, -57.0, 1.8, 135f, 45f)
        takeQualityScreenshots(context, "9_vertical_same_fluid_different_groups")
    }

    private suspend fun testJadeFluidTooltip(context: ClientTestContext) {
        logger.info("Running: testJadeFluidTooltip")
        context.clearArea(basePos, 3, 3, 3)
        context.placeTank(basePos)
        context.insertFluidAt(basePos, fluidVariantOf(Fluids.WATER), FLUID_BUCKET * TANK_CAPACITY / 2)
        context.waitTicks(20)

        // タンク中心 (basePos.y + 0.5) にクロスヘアを合わせるため、
        // プレイヤー目線高さ (1.62) を差し引いた足元 Y を計算
        val tankCenterY = basePos.y + 0.5
        val eyeHeight = 1.62
        context.setupCamera(0.5, tankCenterY - eyeHeight, 2.0, 180f, 0f)
        // Jade のサーバーデータ取得・描画のために長めに待機
        context.waitTicks(40)
        context.takeScreenshot("6_jade_fluid_tooltip")
    }

    private suspend fun testItemInventory(context: ClientTestContext) {
        logger.info("Running: testItemInventory")
        context.runCommand("gamemode survival @p")
        context.waitTicks(5)

        context.runOnServer { srv ->
            val player = srv.playerList.players.firstOrNull() ?: return@runOnServer
            val inventory = player.inventory
            inventory.clearContent()

            var slot = 0
            val water = fluidVariantOf(Fluids.WATER)
            for (tier in TankTier.entries) {
                val block =
                    CTBlocks.ALL_TANKS.firstOrNull {
                        (it as? ConnectedTankBlock)?.tier == tier
                    } ?: continue
                val item = block.asItem()
                val tierCapacity = CTServerConfig.instance.getTierCapacity(tier)

                val halfStack =
                    ItemStack(item).also { stack ->
                        stack.set(
                            CTDataComponentTypes.TANK_FLUID,
                            TankFluidStorage.ExistingData(water, FLUID_BUCKET * tierCapacity / 2),
                        )
                    }
                if (slot < 36) inventory.setItem(slot++, halfStack)

                val fullStack =
                    ItemStack(item).also { stack ->
                        stack.set(
                            CTDataComponentTypes.TANK_FLUID,
                            TankFluidStorage.ExistingData(water, FLUID_BUCKET * tierCapacity),
                        )
                    }
                if (slot < 36) inventory.setItem(slot++, fullStack)
            }
        }
        context.waitTicks(5)

        context.pressKey(GLFW.GLFW_KEY_E)
        context.waitForScreen(InventoryScreen::class.java)
        context.waitTicks(10)
        context.takeScreenshot("10_item_inventory_all_tiers")

        context.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(3)

        context.runCommand("gamemode spectator @p")
        context.waitTicks(5)
    }
}
