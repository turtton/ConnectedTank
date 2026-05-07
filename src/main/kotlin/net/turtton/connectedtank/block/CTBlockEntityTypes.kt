package net.turtton.connectedtank.block

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.turtton.connectedtank.extension.ModIdentifier

object CTBlockEntityTypes {
    val CONNECTED_TANK: BlockEntityType<ConnectedTankBlockEntity> =
        Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ModIdentifier("connected_tank"),
            FabricBlockEntityTypeBuilder.create(::ConnectedTankBlockEntity, *CTBlocks.ALL_TANKS.toTypedArray()).build(),
        )

    fun init() {}
}
