package net.turtton.connectedtank.recipe

import net.minecraft.recipe.RecipeSerializer
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.turtton.connectedtank.extension.ModIdentifier

object CTRecipeSerializers {
    val TANK_UPGRADE: RecipeSerializer<TankUpgradeRecipe> = Registry.register(
        Registries.RECIPE_SERIALIZER,
        ModIdentifier("tank_upgrade"),
        TankUpgradeRecipe.Serializer(),
    )

    fun init() {}
}
