package net.turtton.connectedtank

//? if fabric {
import net.fabricmc.api.ModInitializer
//?} else if neoforge {
/*import net.neoforged.fml.common.Mod*/
//?}
import net.turtton.connectedtank.block.CTBlockEntityTypes
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.item.CTItems
import net.turtton.connectedtank.network.ConfigSyncPayload
import net.turtton.connectedtank.recipe.CTRecipeSerializers
import org.slf4j.LoggerFactory

//? if neoforge {
/*@Mod("connectedtank")*/
//?}
//? if fabric {
object ConnectedTank : ModInitializer {
//?} else if neoforge {
/*object ConnectedTank {*/
//?}
    val logger = LoggerFactory.getLogger("connectedtank")

    //? if fabric {
    override fun onInitialize() {
        init()
    }
    //?}

    fun init() {
        CTServerConfig.load()
        CTDataComponentTypes.init()
        CTBlocks.init()
        CTBlockEntityTypes.init()
        CTItems.init()
        CTRecipeSerializers.init()
        ConfigSyncPayload.registerServer()
    }

    //? if neoforge {
    /*init {
        val modBus = net.neoforged.fml.ModLoadingContext.get().activeContainer.eventBus
        CTServerConfig.load()
        modBus?.addListener(::onRegister)
        modBus?.register(CTBlocks::class.java)
        modBus?.register(ConfigSyncPayload::class.java)
        ConfigSyncPayload.registerServer()
    }

    private fun onRegister(event: net.neoforged.neoforge.registries.RegisterEvent) {
        when (event.registryKey) {
            net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE -> CTDataComponentTypes.init()
            net.minecraft.core.registries.Registries.BLOCK -> CTBlocks.init()
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE -> CTBlockEntityTypes.init()
            net.minecraft.core.registries.Registries.ITEM -> run { CTItems.ALL_TANK_ITEMS }
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB -> CTItems.init()
            net.minecraft.core.registries.Registries.RECIPE_SERIALIZER -> CTRecipeSerializers.init()
        }
    }*/
    //?}
}
