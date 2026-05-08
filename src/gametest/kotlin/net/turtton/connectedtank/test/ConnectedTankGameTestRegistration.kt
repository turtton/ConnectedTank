package net.turtton.connectedtank.test

//? if neoforge {
/*import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInstance
import net.minecraft.gametest.framework.TestData
import net.minecraft.gametest.framework.TestEnvironmentDefinition
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Rotation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import java.lang.reflect.Method
import java.util.function.Consumer

class KotlinGameTestInstance : GameTestInstance {
    private val invoker: Consumer<GameTestHelper>

    constructor(invoker: Consumer<GameTestHelper>, info: TestData<Holder<TestEnvironmentDefinition>>) : super(info) {
        this.invoker = invoker
    }

    constructor(info: TestData<Holder<TestEnvironmentDefinition>>) : super(info) {
        this.invoker = NOOP_INVOKER
    }

    fun infoData(): TestData<Holder<TestEnvironmentDefinition>> = info()

    override fun run(helper: GameTestHelper) {
        invoker.accept(helper)
    }

    override fun codec(): MapCodec<KotlinGameTestInstance> = CODEC

    override fun typeDescription(): MutableComponent = Component.literal("Kotlin Test")

    companion object {
        private val NOOP_INVOKER = Consumer<GameTestHelper> { it.succeed() }

        val CODEC: MapCodec<KotlinGameTestInstance> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(TestData.CODEC.fieldOf("info").forGetter(KotlinGameTestInstance::infoData))
                .apply(instance, ::KotlinGameTestInstance)
        }
    }
}

@EventBusSubscriber(modid = "connectedtank")
object ConnectedTankGameTestRegistration {
    private const val MOD_ID = "connectedtank"
    private const val EMPTY_STRUCTURE = "$MOD_ID:empty"
    private const val MAX_TICKS = 200

    @SubscribeEvent
    fun onRegister(event: net.neoforged.neoforge.registries.RegisterEvent) {
        if (event.registryKey == net.minecraft.core.registries.Registries.TEST_INSTANCE_TYPE) {
            Registry.register(
                BuiltInRegistries.TEST_INSTANCE_TYPE,
                Identifier.parse("$MOD_ID:kotlin_test"),
                KotlinGameTestInstance.CODEC,
            )
        }
    }

    @SubscribeEvent
    fun onRegisterTests(event: RegisterGameTestsEvent) {
        val environment = event.registerEnvironment(
            Identifier.parse("$MOD_ID:default"),
            TestEnvironmentDefinition.AllOf(emptyList()),
        )
        val testMethods = ConnectedTankGameTest::class.java.declaredMethods
            .filter { it.parameterCount == 1 && it.parameterTypes[0] == GameTestHelper::class.java }
            .filter { it.returnType == Void.TYPE }
            .sortedBy { it.name }
        for (method in testMethods) {
            val name = methodToTestName(method)
            val testData = TestData(
                environment,
                Identifier.parse(EMPTY_STRUCTURE),
                MAX_TICKS,
                0,
                true,
                Rotation.NONE,
            )
            val instance = KotlinGameTestInstance(invokerFor(method), testData)
            event.registerTest(Identifier.parse(name), instance)
        }
    }

    private fun methodToTestName(method: Method): String {
        val snake = method.name.fold(StringBuilder()) { sb, c ->
            if (c.isUpperCase() && sb.isNotEmpty()) sb.append('_').append(c.lowercaseChar()) else sb.append(c.lowercaseChar())
        }.toString()
        return "$MOD_ID:$snake"
    }

    private fun invokerFor(method: Method): Consumer<GameTestHelper> {
        method.isAccessible = true
        return Consumer { helper ->
            try {
                method.invoke(ConnectedTankGameTest, helper)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                val cause = e.cause ?: e
                org.slf4j.LoggerFactory.getLogger("ConnectedTankGameTest")
                    .error("Test '${method.name}' threw exception", cause)
                throw cause
            }
        }
    }
}*/
//?}
