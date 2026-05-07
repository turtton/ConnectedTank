package net.turtton.connectedtank.item

import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants
//? if >=1.21.11 {
/*import java.util.function.Consumer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.client.renderer.special.SpecialModelRenderers
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.Identifier
import net.turtton.connectedtank.block.ConnectedTankBlock
import net.turtton.connectedtank.component.CTDataComponentTypes
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTClientConfig.RenderQuality
import net.turtton.connectedtank.config.CTServerConfig
import net.turtton.connectedtank.config.SyncedServerConfig
import net.turtton.connectedtank.render.FluidRenderHelper
import net.turtton.connectedtank.render.WaveParams
import org.joml.Vector3fc

class ConnectedTankItemRenderer : SpecialModelRenderer<ItemStack> {
    override fun extractArgument(stack: ItemStack): ItemStack? = stack

    override fun submit(
        data: ItemStack?,
        displayContext: ItemDisplayContext,
        matrices: PoseStack,
        queue: SubmitNodeCollector,
        light: Int,
        overlay: Int,
        glint: Boolean,
        seed: Int,
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

        matrices.pushPose()
        try {
            FluidRenderHelper.renderFluid(
                queue,
                matrices,
                sprite,
                argb,
                fillLevel,
                WaveParams(animTime = 0f, gridSize = gridSize),
                renderLayer = RenderTypes.itemEntityTranslucentCull(sprite.atlasLocation()),
            )
        } finally {
            matrices.popPose()
        }
    }

    override fun getExtents(vertices: Consumer<Vector3fc>) {
    }

    class Unbaked : SpecialModelRenderer.Unbaked {
        override fun bake(context: SpecialModelRenderer.BakingContext): SpecialModelRenderer<*> =
            ConnectedTankItemRenderer()

        override fun type(): MapCodec<out SpecialModelRenderer.Unbaked> = CODEC

        companion object {
            val CODEC: MapCodec<Unbaked> = MapCodec.unit(Unbaked())
        }
    }

    companion object {
        val ID: Identifier = Identifier.fromNamespaceAndPath("connectedtank", "tank_fluid")

        fun register() {
            SpecialModelRenderers.ID_MAPPER.put(ID, Unbaked.CODEC)
        }
    }
}
*/
//?} else {
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.model.geom.EntityModelSet
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.client.renderer.special.SpecialModelRenderers
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier as ResourceLocation*/
//?} else {
import net.minecraft.resources.ResourceLocation
//?}
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
    override fun extractArgument(stack: ItemStack): ItemStack? = stack

    override fun render(
        data: ItemStack?,
        displayContext: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
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

        matrices.pushPose()
        try {
            FluidRenderHelper.renderFluid(
                vertexConsumers,
                matrices,
                sprite,
                argb,
                fillLevel,
                WaveParams(animTime = 0f, gridSize = gridSize),
                renderLayer = RenderType.itemEntityTranslucentCull(sprite.atlasLocation()),
            )
        } finally {
            matrices.popPose()
        }
    }

    override fun getExtents(vertices: MutableSet<Vector3f>) {
    }

    class Unbaked : SpecialModelRenderer.Unbaked {
        override fun bake(entityModels: EntityModelSet): SpecialModelRenderer<*> = ConnectedTankItemRenderer()

        override fun type(): MapCodec<out SpecialModelRenderer.Unbaked> = CODEC

        companion object {
            val CODEC: MapCodec<Unbaked> = MapCodec.unit(Unbaked())
        }
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("connectedtank", "tank_fluid")

        fun register() {
            SpecialModelRenderers.ID_MAPPER.put(ID, Unbaked.CODEC)
        }
    }
}
//?}
