package net.turtton.connectedtank.component

import net.minecraft.component.ComponentType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.turtton.connectedtank.block.TankFluidStorage
import net.turtton.connectedtank.extension.ModIdentifier

object CTDataComponentTypes {
    val TANK_FLUID: ComponentType<TankFluidStorage.ExistingData> = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        ModIdentifier("tank_fluid"),
        ComponentType.builder<TankFluidStorage.ExistingData>()
            .codec(TankFluidStorage.ExistingData.CODEC)
            .build(),
    )

    fun init() {}
}
