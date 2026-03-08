package net.turtton.connectedtank.block

import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Vec3d

class ConnectedTankBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererFactory.Context,
) : BlockEntityRenderer<ConnectedTankBlockEntity> {
    companion object {
        private const val MARGIN = 2f / 16f
        private const val MIN = MARGIN
        private const val MAX = 1f - MARGIN
    }

    override fun render(
        entity: ConnectedTankBlockEntity,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        overlay: Int,
        cameraPos: Vec3d,
    ) {
        if (entity.fillLevel <= 0f || entity.fluidVariant.isBlank) return

        val sprite = FluidVariantRendering.getSprite(entity.fluidVariant) ?: return
        val color = FluidVariantRendering.getColor(entity.fluidVariant)
        val argb = (0xFF shl 24) or (color and 0x00FFFFFF)

        val renderLayer = RenderLayer.getEntityTranslucent(sprite.atlasId)
        val consumer = vertexConsumers.getBuffer(renderLayer)

        val fluidTop = MIN + (MAX - MIN) * entity.fillLevel
        val fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE
        val ov = OverlayTexture.DEFAULT_UV

        matrices.push()
        val entry = matrices.peek()
        val u0 = sprite.minU
        val u1 = sprite.maxU
        val v0 = sprite.minV
        val v1 = sprite.maxV

        fun VertexConsumer.quad(
            x1: Float,
            y1: Float,
            z1: Float,
            x2: Float,
            y2: Float,
            z2: Float,
            x3: Float,
            y3: Float,
            z3: Float,
            x4: Float,
            y4: Float,
            z4: Float,
            tu0: Float,
            tv0: Float,
            tu1: Float,
            tv1: Float,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            vertex(entry, x1, y1, z1).color(argb).texture(tu0, tv0).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
            vertex(entry, x2, y2, z2).color(argb).texture(tu0, tv1).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
            vertex(entry, x3, y3, z3).color(argb).texture(tu1, tv1).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
            vertex(entry, x4, y4, z4).color(argb).texture(tu1, tv0).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
        }

        // Top (Y+)
        consumer.quad(MIN, fluidTop, MIN, MIN, fluidTop, MAX, MAX, fluidTop, MAX, MAX, fluidTop, MIN, u0, v0, u1, v1, 0f, 1f, 0f)
        // Bottom (Y-)
        consumer.quad(MIN, MIN, MAX, MIN, MIN, MIN, MAX, MIN, MIN, MAX, MIN, MAX, u0, v0, u1, v1, 0f, -1f, 0f)
        // North (Z-)
        consumer.quad(MIN, fluidTop, MIN, MAX, fluidTop, MIN, MAX, MIN, MIN, MIN, MIN, MIN, u0, v0, u1, v1, 0f, 0f, -1f)
        // South (Z+)
        consumer.quad(MAX, fluidTop, MAX, MIN, fluidTop, MAX, MIN, MIN, MAX, MAX, MIN, MAX, u0, v0, u1, v1, 0f, 0f, 1f)
        // West (X-)
        consumer.quad(MIN, fluidTop, MAX, MIN, fluidTop, MIN, MIN, MIN, MIN, MIN, MIN, MAX, u0, v0, u1, v1, -1f, 0f, 0f)
        // East (X+)
        consumer.quad(MAX, fluidTop, MIN, MAX, fluidTop, MAX, MAX, MIN, MAX, MAX, MIN, MIN, u0, v0, u1, v1, 1f, 0f, 0f)

        matrices.pop()
    }
}
