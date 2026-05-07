package net.turtton.connectedtank.component

import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.extension.ModIdentifier

object CTDataComponentTypes {
    val TANK_FLUID: DataComponentType<TankFluidStorage.ExistingData> = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ModIdentifier("tank_fluid"),
        DataComponentType.builder<TankFluidStorage.ExistingData>()
            .persistent(TankFluidStorage.ExistingData.CODEC)
            .build(),
    )

    fun init() {}
}
