package net.turtton.connectedtank.extension

//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
import net.turtton.connectedtank.MOD_ID

@Suppress("FunctionName")
fun ModIdentifier(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
