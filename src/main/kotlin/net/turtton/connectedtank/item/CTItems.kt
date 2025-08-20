package net.turtton.connectedtank.item

import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.extension.ModIdentifier

object CTItems {
    val CONNECTED_TANK = register("connected_tank", { BlockItem(CTBlocks.CONNECTED_TANK, it) })

    private fun <I : Item> register(
        name: String,
        factory: (Item.Settings) -> I,
        settingsFactory: Item.Settings.() -> Unit = {},
    ): I {
        val itemKey = RegistryKey.of(RegistryKeys.ITEM, ModIdentifier(name))
        val settings = Item.Settings().apply(settingsFactory)
        val item = factory(settings.registryKey(itemKey))
        Registry.register(Registries.ITEM, itemKey, item)
        return item
    }

    fun init() {}
}
