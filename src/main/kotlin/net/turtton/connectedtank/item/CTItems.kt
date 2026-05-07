package net.turtton.connectedtank.item

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
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
    val NETHERITE_CONNECTED_TANK = registerTank(TankTier.NETHERITE) { fireResistant() }

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
        additionalSettings: Item.Properties.() -> Unit = {},
    ): BlockItem {
        val block = CTBlocks.ALL_TANKS.first { (it as? net.turtton.connectedtank.block.ConnectedTankBlock)?.tier == tier }
        return register(tier.id, { BlockItem(block, it) }) {
            useBlockDescriptionPrefix()
            additionalSettings()
        }
    }

    private fun <I : Item> register(
        name: String,
        factory: (Item.Properties) -> I,
        settingsFactory: Item.Properties.() -> Unit = {},
    ): I {
        val itemKey = ResourceKey.create(Registries.ITEM, ModIdentifier(name))
        val settings = Item.Properties().apply(settingsFactory)
        val item = factory(settings.setId(itemKey))
        Registry.register(BuiltInRegistries.ITEM, itemKey, item)
        return item
    }

    private val ITEM_GROUP_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModIdentifier("item_group"))

    fun init() {
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ITEM_GROUP_KEY,
            FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.connectedtank.item_group"))
                .icon { ItemStack(CONNECTED_TANK) }
                .displayItems { _, entries ->
                    ALL_TANK_ITEMS.forEach { entries.accept(it) }
                }
                .build(),
        )
    }
}
