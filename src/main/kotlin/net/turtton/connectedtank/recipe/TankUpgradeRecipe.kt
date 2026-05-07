package net.turtton.connectedtank.recipe

import com.mojang.serialization.MapCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.CraftingInput
//? if <26.1 {
import net.minecraft.core.HolderLookup
//?}
import net.minecraft.world.level.Level
import net.turtton.connectedtank.component.CTDataComponentTypes

class TankUpgradeRecipe(private val shaped: ShapedRecipe) : CraftingRecipe {
    override fun matches(input: CraftingInput, world: Level): Boolean = shaped.matches(input, world)

    //? if >=26.1 {
    /*override fun assemble(input: CraftingInput): ItemStack {
        val result = shaped.assemble(input)
     */
    //?} else {
    override fun assemble(input: CraftingInput, lookup: HolderLookup.Provider): ItemStack {
        val result = shaped.assemble(input, lookup)
        //?}
        for (stack in input.items()) {
            val fluidData = stack.get(CTDataComponentTypes.TANK_FLUID)
            if (fluidData != null) {
                result.set(CTDataComponentTypes.TANK_FLUID, fluidData)
                break
            }
        }
        return result
    }

    override fun getSerializer(): RecipeSerializer<out CraftingRecipe> = CTRecipeSerializers.TANK_UPGRADE

    override fun group(): String = shaped.group()

    override fun category(): CraftingBookCategory = shaped.category()

    override fun placementInfo(): PlacementInfo = shaped.placementInfo()

    override fun showNotification(): Boolean = shaped.showNotification()

    override fun display(): List<RecipeDisplay> = shaped.display()

    companion object {
        //? if >=26.1 {
        /*val MAP_CODEC: MapCodec<TankUpgradeRecipe> = ShapedRecipe.MAP_CODEC.xmap(::TankUpgradeRecipe) { it.shaped }
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, TankUpgradeRecipe> = ShapedRecipe.STREAM_CODEC.map(::TankUpgradeRecipe) { it.shaped }*/
        //?}
    }

    //? if <26.1 {
    class Serializer : RecipeSerializer<TankUpgradeRecipe> {
        override fun codec(): MapCodec<TankUpgradeRecipe> = ShapedRecipe.Serializer.CODEC.xmap(::TankUpgradeRecipe) { it.shaped }

        @Deprecated("Recipe is no longer synced to clients")
        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, TankUpgradeRecipe> = ShapedRecipe.Serializer.STREAM_CODEC.map(::TankUpgradeRecipe) { it.shaped }
    }
    //?}
}
