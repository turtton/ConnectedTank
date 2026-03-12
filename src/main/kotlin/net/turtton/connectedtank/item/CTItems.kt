package net.turtton.connectedtank.item

import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.TankTier
import net.turtton.connectedtank.extension.ModIdentifier

object CTItems {
    val CONNECTED_TANK = registerTank(TankTier.BASE)
    val WOOD_CONNECTED_TANK = registerTank(TankTier.WOOD)
    val STONE_CONNECTED_TANK = registerTank(TankTier.STONE)
    val COPPER_CONNECTED_TANK = registerTank(TankTier.COPPER)
    val IRON_CONNECTED_TANK = registerTank(TankTier.IRON)
    val GOLD_CONNECTED_TANK = registerTank(TankTier.GOLD)
    val DIAMOND_CONNECTED_TANK = registerTank(TankTier.DIAMOND)
    val NETHERITE_CONNECTED_TANK = registerTank(TankTier.NETHERITE)

    val ALL_TANK_ITEMS: List<BlockItem> = listOf(
        CONNECTED_TANK,
        WOOD_CONNECTED_TANK,
        STONE_CONNECTED_TANK,
        COPPER_CONNECTED_TANK,
        IRON_CONNECTED_TANK,
        GOLD_CONNECTED_TANK,
        DIAMOND_CONNECTED_TANK,
        NETHERITE_CONNECTED_TANK,
    )

    private fun registerTank(tier: TankTier): BlockItem {
        val block = CTBlocks.ALL_TANKS.first { (it as? net.turtton.connectedtank.block.ConnectedTankBlock)?.tier == tier }
        return register(tier.id, { BlockItem(block, it) })
    }

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
