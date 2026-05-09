package net.turtton.connectedtank.test

//? if fabric {
import net.fabricmc.api.ClientModInitializer

//?} else if neoforge {
/*import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
*/
//?}

private const val PROPERTY_KEY = "connectedtank.clienttest"

//? if fabric {
object ClientTestEntry : ClientModInitializer {
    override fun onInitializeClient() {
        if (System.getProperty(PROPERTY_KEY) == null) return
        ClientTestRunner.start()
    }
}

//?} else if neoforge {
/*@EventBusSubscriber(value = [Dist.CLIENT], modid = "connectedtank")
object ClientTestEntry {
    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        if (System.getProperty(PROPERTY_KEY) == null) return
        ClientTestRunner.start()
    }
}
*/
//?}
