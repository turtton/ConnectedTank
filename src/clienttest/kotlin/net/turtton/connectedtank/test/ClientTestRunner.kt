package net.turtton.connectedtank.test

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.Difficulty
//? if <26.1 {
import net.minecraft.world.flag.FeatureFlags
//?}
//? if >=1.21.11 && <26.1 {
/*import net.minecraft.world.level.gamerules.GameRules
*/
//?} else if <1.21.11 {
import net.minecraft.world.level.GameRules

//?}
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

object ClientTestRunner {
    private val logger = LoggerFactory.getLogger("ConnectedTankClientTest")
    private val minecraft get() = Minecraft.getInstance()

    val clientDispatcher: CoroutineDispatcher =
        object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                minecraft.execute(block)
            }
        }

    fun start() {
        CoroutineScope(clientDispatcher + CoroutineName("ClientTest")).launch {
            var success = false
            try {
                logger.info("Starting client tests...")

                waitUntil { minecraft.screen != null }
                logger.info("Game initialized, creating test world...")

                createTestWorld()

                waitUntil { minecraft.level != null && minecraft.player != null }
                waitTicks(60)

                logger.info("Test world loaded, running tests...")

                val context = ClientTestContext(minecraft)
                ConnectedTankClientTest.runAll(context)

                success = true
                logger.info("All client tests passed!")
            } catch (e: Exception) {
                logger.error("Client test failed", e)
            } finally {
                logger.info(if (success) "Tests PASSED" else "Tests FAILED")
                // disconnect() は runTick() 内の glfwWaitEventsTimeout() で
                // Xvfb 環境下ブロックするため、halt() で強制終了する
                Runtime.getRuntime().halt(if (success) 0 else 1)
            }
        }
    }

    private fun createTestWorld() {
        //? if >=26.1 {
        /*val levelSettings =
            LevelSettings(
                "connectedtank_test",
                GameType.SPECTATOR,
                LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT,
            )*/
        //?} else {
        val levelSettings =
            LevelSettings(
                "connectedtank_test",
                GameType.SPECTATOR,
                false,
                Difficulty.PEACEFUL,
                true,
                GameRules(FeatureFlags.DEFAULT_FLAGS),
                WorldDataConfiguration.DEFAULT,
            )
        //?}
        val worldOptions = WorldOptions.testWorldWithRandomSeed()
        minecraft.createWorldOpenFlows().createFreshLevel(
            "connectedtank_test",
            levelSettings,
            worldOptions,
            { provider -> WorldPresets.createFlatWorldDimensions(provider) },
            GenericMessageScreen(Component.literal("Failed to create test world")),
        )
    }

    private suspend fun waitTicks(ticks: Int) {
        repeat(ticks) { delay(50) }
    }

    private suspend fun waitUntil(
        timeoutTicks: Int = 600,
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
}
