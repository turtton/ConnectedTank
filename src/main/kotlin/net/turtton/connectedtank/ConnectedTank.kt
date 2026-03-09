package net.turtton.connectedtank

import net.fabricmc.api.ModInitializer
import net.turtton.connectedtank.block.CTBlockEntityTypes
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.item.CTItems
import net.turtton.connectedtank.network.ConfigSyncPayload
import org.slf4j.LoggerFactory

object ConnectedTank : ModInitializer {
    val logger = LoggerFactory.getLogger("connectedtank")

    override fun onInitialize() {
        CTServerConfig.load()
        CTDataComponentTypes.init()
        CTBlocks.init()
        CTBlockEntityTypes.init()
        CTItems.init()
        ConfigSyncPayload.registerServer()
    }
}
