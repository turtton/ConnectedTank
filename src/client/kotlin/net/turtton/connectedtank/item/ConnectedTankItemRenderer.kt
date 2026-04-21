package net.turtton.connectedtank.item

import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.model.LoadedEntityModels
import net.minecraft.client.render.item.model.special.SpecialModelRenderer
import net.minecraft.client.render.item.model.special.SpecialModelTypes
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTClientConfig.RenderQuality
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.config.SyncedServerConfig
import net.turtton.connectedtank.render.FluidRenderHelper
import net.turtton.connectedtank.render.WaveParams
import org.joml.Vector3f

class ConnectedTankItemRenderer : SpecialModelRenderer<ItemStack> {
    override fun getData(stack: ItemStack): ItemStack? = stack

    override fun render(
        data: ItemStack?,
        displayContext: ItemDisplayContext,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        glint: Boolean,
    ) {
        data ?: return
        val fluidData = data.get(CTDataComponentTypes.TANK_FLUID) ?: return
        if (fluidData.variant.isBlank || fluidData.amount <= 0L) return

        val sprite = FluidVariantRendering.getSprite(fluidData.variant) ?: return
        val color = FluidVariantRendering.getColor(fluidData.variant)
        val argb = (0xFF shl 24) or (color and 0x00FFFFFF)

        val tankBlock = (data.item as? BlockItem)?.block as? ConnectedTankBlock ?: return
        val serverConfig = SyncedServerConfig.syncedConfig ?: CTServerConfig.instance
        val capacity = serverConfig.getTierCapacity(tankBlock.tier) * FluidConstants.BUCKET
        if (capacity <= 0L) return
        val fillLevel = (fluidData.amount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)

        val quality = CTClientConfig.instance.renderQuality
        val gridSize = if (quality == RenderQuality.HIGH) 8 else 4

        matrices.push()
        try {
            FluidRenderHelper.renderFluid(
                vertexConsumers,
                matrices,
                sprite,
                argb,
                fillLevel,
                WaveParams(animTime = 0f, gridSize = gridSize),
                renderLayer = RenderLayer.getItemEntityTranslucentCull(sprite.atlasId),
            )
        } finally {
            matrices.pop()
        }
    }

    override fun collectVertices(vertices: MutableSet<Vector3f>) {
    }

    class Unbaked : SpecialModelRenderer.Unbaked {
        override fun bake(entityModels: LoadedEntityModels): SpecialModelRenderer<*> = ConnectedTankItemRenderer()

        override fun getCodec(): MapCodec<out SpecialModelRenderer.Unbaked> = CODEC

        companion object {
            val CODEC: MapCodec<Unbaked> = MapCodec.unit(Unbaked())
        }
    }

    companion object {
        val ID: Identifier = Identifier.of("connectedtank", "tank_fluid")

        fun register() {
            SpecialModelTypes.ID_MAPPER.put(ID, Unbaked.CODEC)
        }
    }
}
