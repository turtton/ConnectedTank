package net.turtton.connectedtank.extension

import net.minecraft.util.Identifier
import net.turtton.connectedtank.MOD_ID

internal fun ModIdentifier(path: String): Identifier = Identifier.of(MOD_ID, path)