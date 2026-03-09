package net.turtton.connectedtank.test

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.fluid.Fluids
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.world.FluidStoragePersistentState
import org.apache.commons.lang3.function.FailableConsumer
import org.lwjgl.glfw.GLFW

object ConnectedTankClientGameTest : FabricClientGameTest {
    private fun TestServerContext.onServer(action: (MinecraftServer) -> Unit) {
        runOnServer(FailableConsumer<MinecraftServer, RuntimeException> { action(it) })
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { singleplayer ->
            val server = singleplayer.server
            singleplayer.clientWorld.waitForChunksRender()

            server.runCommand("gamemode spectator @p")
            context.waitTicks(5)
            context.input.pressKey(GLFW.GLFW_KEY_F1)
            context.waitTicks(5)

            testEmptyTank(context, server)
            testFullWaterTank(context, server)
            testHalfWaterTank(context, server)
            testHorizontalConnectedTanks(context, server)
            testVerticalConnectedTanks(context, server)
        }
    }

    private fun clearArea(server: TestServerContext, basePos: BlockPos, sizeX: Int, sizeY: Int, sizeZ: Int) {
        server.onServer { srv ->
            val world = srv.getWorld(World.OVERWORLD)!!
            val state = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            for (x in 0 until sizeX) {
                for (y in 0 until sizeY) {
                    for (z in 0 until sizeZ) {
                        val pos = basePos.add(x, y, z)
                        if (state.getStorage(pos) != null) {
                            state.removeStorage(pos)
                        }
                        world.removeBlock(pos, false)
                    }
                }
            }
        }
    }

    private fun placeTank(
        server: TestServerContext,
        pos: BlockPos,
        fluid: TankFluidStorage.ExistingData? = null,
    ) {
        server.onServer { srv ->
            val world = srv.getWorld(World.OVERWORLD)!!
            world.setBlockState(pos, CTBlocks.CONNECTED_TANK.defaultState)
            val persistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            val storage = TankFluidStorage(fluid = fluid)
            persistentState.addStorage(pos, storage)
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
        }
    }

    private fun insertFluid(
        server: TestServerContext,
        pos: BlockPos,
        variant: FluidVariant,
        amount: Long,
    ) {
        server.onServer { srv ->
            val world = srv.getWorld(World.OVERWORLD)!!
            val persistentState = world.persistentStateManager.getOrCreate(FluidStoragePersistentState.TYPE)
            val storage = persistentState.getStorage(pos) ?: error("Storage not found at $pos")
            Transaction.openOuter().use { tx ->
                storage.insert(variant, amount, tx)
                tx.commit()
            }
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
        }
    }

    private fun setupCamera(
        context: ClientGameTestContext,
        server: TestServerContext,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    ) {
        // tp を 2 回実行: スペクテイターモードの慣性ドリフトで
        // 1 回目の tp 後にカメラ位置がずれるのを防ぐ
        server.runCommand("tp @p $x $y $z $yaw $pitch")
        context.waitTicks(3)
        server.runCommand("tp @p $x $y $z $yaw $pitch")
        context.waitTicks(1)
    }

    private val basePos = BlockPos(0, -60, 0)

    private fun testEmptyTank(context: ClientGameTestContext, server: TestServerContext) {
        clearArea(server, basePos, 3, 3, 3)
        placeTank(server, basePos)
        context.waitTicks(20)
        setupCamera(context, server, 1.8, -58.5, 1.8, 135f, 50f)
        context.takeScreenshot("1_empty_tank")
    }

    private fun testFullWaterTank(context: ClientGameTestContext, server: TestServerContext) {
        clearArea(server, basePos, 3, 3, 3)
        placeTank(server, basePos)
        insertFluid(server, basePos, FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET * 10)
        context.waitTicks(20)
        setupCamera(context, server, 1.8, -58.5, 1.8, 135f, 50f)
        context.takeScreenshot("2_full_water_tank")
    }

    private fun testHalfWaterTank(context: ClientGameTestContext, server: TestServerContext) {
        clearArea(server, basePos, 3, 3, 3)
        placeTank(server, basePos)
        insertFluid(server, basePos, FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET * 5)
        context.waitTicks(20)
        setupCamera(context, server, 1.8, -58.5, 1.8, 135f, 50f)
        context.takeScreenshot("3_half_water_tank")
    }

    private fun testHorizontalConnectedTanks(context: ClientGameTestContext, server: TestServerContext) {
        clearArea(server, basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.east()
        placeTank(server, pos1)
        placeTank(server, pos2)
        insertFluid(server, pos1, FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET * 10)
        context.waitTicks(20)
        setupCamera(context, server, 2.5, -58.5, 2.5, 135f, 45f)
        context.takeScreenshot("4_horizontal_connected_tanks")
    }

    private fun testVerticalConnectedTanks(context: ClientGameTestContext, server: TestServerContext) {
        clearArea(server, basePos, 3, 3, 3)
        val pos1 = basePos
        val pos2 = basePos.up()
        placeTank(server, pos1)
        placeTank(server, pos2)
        insertFluid(server, pos1, FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET * 10)
        context.waitTicks(20)
        setupCamera(context, server, 1.8, -57.0, 1.8, 135f, 45f)
        context.takeScreenshot("5_vertical_connected_tanks")
    }
}
