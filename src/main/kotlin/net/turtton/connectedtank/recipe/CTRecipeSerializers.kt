package net.turtton.connectedtank.recipe

import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.turtton.connectedtank.extension.ModIdentifier

object CTRecipeSerializers {
    val TANK_UPGRADE: RecipeSerializer<TankUpgradeRecipe> = Registry.register(
        BuiltInRegistries.RECIPE_SERIALIZER,
        ModIdentifier("tank_upgrade"),
        //? if >=26.1 {
        /*RecipeSerializer(TankUpgradeRecipe.MAP_CODEC, TankUpgradeRecipe.STREAM_CODEC),*/
        //?} else {
        TankUpgradeRecipe.Serializer(),
        //?}
    )

    fun init() {}
}
