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
        private const val INSET = 0.001f
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

        val world = entity.world
        val pos = entity.pos
        val tankBlock = CTBlocks.CONNECTED_TANK

        val hasDown = world?.getBlockState(pos.down())?.isOf(tankBlock) == true
        val hasUp = world?.getBlockState(pos.up())?.isOf(tankBlock) == true
        val hasNorth = world?.getBlockState(pos.north())?.isOf(tankBlock) == true
        val hasSouth = world?.getBlockState(pos.south())?.isOf(tankBlock) == true
        val hasWest = world?.getBlockState(pos.west())?.isOf(tankBlock) == true
        val hasEast = world?.getBlockState(pos.east())?.isOf(tankBlock) == true

        val minX = if (hasWest) 0f else INSET
        val maxX = if (hasEast) 1f else 1f - INSET
        val minY = if (hasDown) 0f else INSET
        val minZ = if (hasNorth) 0f else INSET
        val maxZ = if (hasSouth) 1f else 1f - INSET

        val fluidTop = if (hasUp) 1f else minY + (1f - INSET - minY) * entity.fillLevel

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
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            vertex(entry, x1, y1, z1).color(argb).texture(u0, v0).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
            vertex(entry, x2, y2, z2).color(argb).texture(u0, v1).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
            vertex(entry, x3, y3, z3).color(argb).texture(u1, v1).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
            vertex(entry, x4, y4, z4).color(argb).texture(u1, v0).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
        }

        if (!hasUp) {
            consumer.quad(minX, fluidTop, minZ, minX, fluidTop, maxZ, maxX, fluidTop, maxZ, maxX, fluidTop, minZ, 0f, 1f, 0f)
        }
        if (!hasDown) {
            consumer.quad(minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, 0f, -1f, 0f)
        }
        if (!hasNorth) {
            consumer.quad(minX, fluidTop, minZ, maxX, fluidTop, minZ, maxX, minY, minZ, minX, minY, minZ, 0f, 0f, -1f)
        }
        if (!hasSouth) {
            consumer.quad(maxX, fluidTop, maxZ, minX, fluidTop, maxZ, minX, minY, maxZ, maxX, minY, maxZ, 0f, 0f, 1f)
        }
        if (!hasWest) {
            consumer.quad(minX, fluidTop, maxZ, minX, fluidTop, minZ, minX, minY, minZ, minX, minY, maxZ, -1f, 0f, 0f)
        }
        if (!hasEast) {
            consumer.quad(maxX, fluidTop, minZ, maxX, fluidTop, maxZ, maxX, minY, maxZ, maxX, minY, minZ, 1f, 0f, 0f)
        }

        matrices.pop()
    }
}
