package net.turtton.connectedtank.block

import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.turtton.connectedtank.extension.ModIdentifier

object CTBlocks {
    val CONNECTED_TANK = register("connected_tank", ::Block)

    private fun register(
        name: String,
        factory: (AbstractBlock.Settings) -> Block,
        settingsFactory: AbstractBlock.Settings.() -> Unit = {}
    ): Block {
        val blockKey = RegistryKey.of(RegistryKeys.BLOCK, ModIdentifier(name))
        val settings = AbstractBlock.Settings.create().apply(settingsFactory)
        val block = factory(settings.registryKey(blockKey))
        return Registry.register(Registries.BLOCK, blockKey, block)
    }

    fun init() {}
}