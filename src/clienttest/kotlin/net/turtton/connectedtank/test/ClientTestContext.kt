package net.turtton.connectedtank.test

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.fluid.PlatformFluidVariant
import net.turtton.connectedtank.fluid.insertFluid
import net.turtton.connectedtank.world.FluidStoragePersistentState
import org.slf4j.LoggerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ClientTestContext(val minecraft: Minecraft) {
    private val logger = LoggerFactory.getLogger("ClientTestContext")

    val server: MinecraftServer
        get() = minecraft.singleplayerServer ?: error("Not in singleplayer")

    suspend fun waitTicks(ticks: Int) {
        repeat(ticks) { delay(50) }
    }

    suspend fun waitUntil(
        timeoutTicks: Int = 300,
        condition: () -> Boolean,
    ) {
        var waited = 0
        while (!condition()) {
            if (waited++ > timeoutTicks) {
                error("Timeout waiting for condition after $timeoutTicks ticks")
            }
            delay(50)
        }
    }

    fun takeScreenshot(name: String) {
        Screenshot.grab(
            minecraft.gameDirectory,
            "$name.png",
            minecraft.mainRenderTarget,
            1,
        ) { component ->
            logger.info("Screenshot: {}", component.string)
        }
    }

    suspend fun runOnServer(action: (MinecraftServer) -> Unit) {
        suspendCancellableCoroutine { cont ->
            server.execute {
                if (cont.isActive) {
                    try {
                        action(server)
                        cont.resume(Unit)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            }
        }
    }

    suspend fun runCommand(command: String) {
        runOnServer { srv ->
            val trimmed = if (command.startsWith("/")) command.substring(1) else command
            val source = srv.createCommandSourceStack()
            srv.commands.dispatcher.execute(trimmed, source)
        }
    }

    fun pressKey(key: Int) {
        //? if >=1.21.11 {
        /*val window = minecraft.window.handle()
        val keyEvent = net.minecraft.client.input.KeyEvent(key, 0, 0)
        val method = minecraft.keyboardHandler.javaClass.getDeclaredMethod(
            "keyPress",
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            net.minecraft.client.input.KeyEvent::class.java,
        )
        method.isAccessible = true
        method.invoke(minecraft.keyboardHandler, window, 1, keyEvent)
        method.invoke(minecraft.keyboardHandler, window, 0, keyEvent)
         */
        //?} else {
        val window = minecraft.window.window
        minecraft.keyboardHandler.keyPress(window, key, 0, 1, 0)
        minecraft.keyboardHandler.keyPress(window, key, 0, 0, 0)

        //?}
    }

    suspend fun <T : Screen> waitForScreen(
        screenClass: Class<T>,
        timeoutTicks: Int = 300,
    ) {
        waitUntil(timeoutTicks) { screenClass.isInstance(minecraft.screen) }
    }

    suspend fun clearArea(
        basePos: BlockPos,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
    ) {
        runOnServer { srv ->
            val world = srv.getLevel(Level.OVERWORLD)!!
            val state = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
            for (x in 0 until sizeX) {
                for (y in 0 until sizeY) {
                    for (z in 0 until sizeZ) {
                        val pos = basePos.offset(x, y, z)
                        if (state.getStorage(pos) != null) {
                            state.removeStorage(pos)
                        }
                        world.removeBlock(pos, false)
                    }
                }
            }
        }
    }

    suspend fun placeTank(
        pos: BlockPos,
        fluid: TankFluidStorage.ExistingData? = null,
    ) {
        runOnServer { srv ->
            val world = srv.getLevel(Level.OVERWORLD)!!
            world.setBlockAndUpdate(pos, CTBlocks.CONNECTED_TANK.defaultBlockState())
            val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
            val storage = TankFluidStorage(fluid = fluid)
            persistentState.addStorage(pos, storage)
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
        }
    }

    suspend fun placeIsolatedTank(
        pos: BlockPos,
        fluid: TankFluidStorage.ExistingData,
    ) {
        runOnServer { srv ->
            val world = srv.getLevel(Level.OVERWORLD)!!
            world.setBlockAndUpdate(pos, CTBlocks.CONNECTED_TANK.defaultBlockState())
            val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
            val storage = TankFluidStorage(fluid = fluid)
            persistentState.addIsolatedStorage(pos, storage)
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
        }
    }

    suspend fun insertFluidAt(
        pos: BlockPos,
        variant: PlatformFluidVariant,
        amount: Long,
    ) {
        runOnServer { srv ->
            val world = srv.getLevel(Level.OVERWORLD)!!
            val persistentState = world.dataStorage.computeIfAbsent(FluidStoragePersistentState.TYPE)
            val storage = persistentState.getStorage(pos) ?: error("Storage not found at $pos")
            insertFluid(storage, variant, amount)
            CTBlocks.syncGroupBlockEntities(world, pos, persistentState)
        }
    }

    suspend fun setupCamera(
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
    ) {
        // tp を 2 回実行: スペクテイターモードの慣性ドリフトで
        // 1 回目の tp 後にカメラ位置がずれるのを防ぐ
        runCommand("tp @p $x $y $z $yaw $pitch")
        waitTicks(3)
        runCommand("tp @p $x $y $z $yaw $pitch")
        waitTicks(1)
    }
}
