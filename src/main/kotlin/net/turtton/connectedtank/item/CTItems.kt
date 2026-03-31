package net.turtton.connectedtank.item

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.text.Text
import net.turtton.connectedtank.block.CTBlocks
import net.turtton.connectedtank.block.TankTier
import net.turtton.connectedtank.extension.ModIdentifier

object CTItems {
    val CONNECTED_TANK = registerTank(TankTier.BASE)
    val STONE_CONNECTED_TANK = registerTank(TankTier.STONE)
    val COPPER_CONNECTED_TANK = registerTank(TankTier.COPPER)
    val IRON_CONNECTED_TANK = registerTank(TankTier.IRON)
    val GOLD_CONNECTED_TANK = registerTank(TankTier.GOLD)
    val DIAMOND_CONNECTED_TANK = registerTank(TankTier.DIAMOND)
    val NETHERITE_CONNECTED_TANK = registerTank(TankTier.NETHERITE) { fireproof() }

    val ALL_TANK_ITEMS: List<BlockItem> = listOf(
        CONNECTED_TANK,
        STONE_CONNECTED_TANK,
        COPPER_CONNECTED_TANK,
        IRON_CONNECTED_TANK,
        GOLD_CONNECTED_TANK,
        DIAMOND_CONNECTED_TANK,
        NETHERITE_CONNECTED_TANK,
    )

    private fun registerTank(
        tier: TankTier,
        additionalSettings: Item.Settings.() -> Unit = {},
    ): BlockItem {
        val block = CTBlocks.ALL_TANKS.first { (it as? net.turtton.connectedtank.block.ConnectedTankBlock)?.tier == tier }
        return register(tier.id, { BlockItem(block, it) }) {
            useBlockPrefixedTranslationKey()
            additionalSettings()
        }
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

    private val ITEM_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, ModIdentifier("item_group"))

    fun init() {
        Registry.register(
            Registries.ITEM_GROUP,
            ITEM_GROUP_KEY,
            FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.connectedtank.item_group"))
                .icon { ItemStack(CONNECTED_TANK) }
                .entries { _, entries ->
                    ALL_TANK_ITEMS.forEach { entries.add(it) }
                }
                .build(),
        )
    }
}
