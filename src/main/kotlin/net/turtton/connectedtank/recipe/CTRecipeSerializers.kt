package net.turtton.connectedtank.recipe

import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.turtton.connectedtank.extension.ModIdentifier

object CTRecipeSerializers {
    val TANK_UPGRADE: RecipeSerializer<TankUpgradeRecipe> = Registry.register(
        BuiltInRegistries.RECIPE_SERIALIZER,
        ModIdentifier("tank_upgrade"),
        TankUpgradeRecipe.Serializer(),
    )

    fun init() {}
}
