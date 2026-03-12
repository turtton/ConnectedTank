package net.turtton.connectedtank.recipe

import com.mojang.serialization.MapCodec
import net.minecraft.item.ItemStack
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.recipe.CraftingRecipe
import net.minecraft.recipe.IngredientPlacement
import net.minecraft.recipe.RecipeSerializer
import net.minecraft.recipe.ShapedRecipe
import net.minecraft.recipe.book.CraftingRecipeCategory
import net.minecraft.recipe.display.RecipeDisplay
import net.minecraft.recipe.input.CraftingRecipeInput
import net.minecraft.registry.RegistryWrapper
import net.minecraft.world.World
import net.turtton.connectedtank.component.CTDataComponentTypes

class TankUpgradeRecipe(private val shaped: ShapedRecipe) : CraftingRecipe {
    override fun matches(input: CraftingRecipeInput, world: World): Boolean = shaped.matches(input, world)

    override fun craft(input: CraftingRecipeInput, lookup: RegistryWrapper.WrapperLookup): ItemStack {
        val result = shaped.craft(input, lookup)
        for (stack in input.stacks) {
            val fluidData = stack.get(CTDataComponentTypes.TANK_FLUID)
            if (fluidData != null) {
                result.set(CTDataComponentTypes.TANK_FLUID, fluidData)
                break
            }
        }
        return result
    }

    override fun getSerializer(): RecipeSerializer<out CraftingRecipe> = CTRecipeSerializers.TANK_UPGRADE

    override fun getGroup(): String = shaped.group

    override fun getCategory(): CraftingRecipeCategory = shaped.category

    override fun getIngredientPlacement(): IngredientPlacement = shaped.ingredientPlacement

    override fun showNotification(): Boolean = shaped.showNotification()

    override fun getDisplays(): List<RecipeDisplay> = shaped.displays

    class Serializer : RecipeSerializer<TankUpgradeRecipe> {
        override fun codec(): MapCodec<TankUpgradeRecipe> = ShapedRecipe.Serializer.CODEC.xmap(::TankUpgradeRecipe) { it.shaped }

        @Deprecated("Recipe is no longer synced to clients")
        override fun packetCodec(): PacketCodec<RegistryByteBuf, TankUpgradeRecipe> = ShapedRecipe.Serializer.PACKET_CODEC.xmap(::TankUpgradeRecipe) { it.shaped }
    }
}
