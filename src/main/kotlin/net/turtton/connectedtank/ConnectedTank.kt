package net.turtton.connectedtank

import net.fabricmc.api.ModInitializer
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.item.CTItems
import org.slf4j.LoggerFactory

object ConnectedTank : ModInitializer {
    private val logger = LoggerFactory.getLogger("connectedtank")

    override fun onInitialize() {
        CTBlocks.init()
        CTItems.init()
    }
}
