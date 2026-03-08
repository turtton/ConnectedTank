package net.turtton.connectedtank.block

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.turtton.connectedtank.extension.ModIdentifier

object CTBlockEntityTypes {
    val CONNECTED_TANK: BlockEntityType<ConnectedTankBlockEntity> =
        Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            ModIdentifier("connected_tank"),
            FabricBlockEntityTypeBuilder.create(::ConnectedTankBlockEntity, CTBlocks.CONNECTED_TANK).build(),
        )

    fun init() {}
}
