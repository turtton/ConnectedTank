package net.turtton.connectedtank.block

import kotlin.math.max
import kotlin.math.sin
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTClientConfig.RenderQuality

class ConnectedTankBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererFactory.Context,
) : BlockEntityRenderer<ConnectedTankBlockEntity> {
    companion object {
        private const val INSET = 0.001f

        private const val AMPLITUDE = 0.02f
        private const val FREQUENCY = 1.2f
        private const val SPEED = 0.25f
        private const val SECONDARY_AMP = 0.01f
        private const val SECONDARY_FREQ = 1.6f
        private const val SECONDARY_SPEED = 0.18f

        private const val DECAY_TICKS = 60f
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
        if (entity.localFillLevel <= 0f || entity.fluidVariant.isBlank) return

        val sprite = FluidVariantRendering.getSprite(entity.fluidVariant) ?: return
        val color = FluidVariantRendering.getColor(entity.fluidVariant)
        val argb = (0xFF shl 24) or (color and 0x00FFFFFF)

        val renderLayer = RenderLayer.getEntityTranslucent(sprite.atlasId)
        val consumer = vertexConsumers.getBuffer(renderLayer)

        val world = entity.world
        val pos = entity.pos

        val myGroupId = entity.groupId
        fun sameGroupNeighbor(neighborPos: BlockPos): ConnectedTankBlockEntity? {
            if (myGroupId == null) return null
            val neighbor = world?.getBlockEntity(neighborPos) as? ConnectedTankBlockEntity ?: return null
            return if (neighbor.groupId == myGroupId) neighbor else null
        }

        val neighborDown = sameGroupNeighbor(pos.down())
        val neighborUp = sameGroupNeighbor(pos.up())

        // 垂直: 下タンクが満杯で同一グループのときのみ連続とみなす
        val hasDown = neighborDown != null && neighborDown.localFillLevel >= 1.0f
        // 垂直: 自身が満杯かつ上タンクに同一グループの液体があるときのみ上面を省略
        val hasUp = neighborUp != null && entity.localFillLevel >= 1.0f && neighborUp.localFillLevel > 0f
        // 水平: 同一グループで液体が存在する隣接タンクのみ連続
        val hasNorth = sameGroupNeighbor(pos.north())?.let { it.localFillLevel > 0f } == true
        val hasSouth = sameGroupNeighbor(pos.south())?.let { it.localFillLevel > 0f } == true
        val hasWest = sameGroupNeighbor(pos.west())?.let { it.localFillLevel > 0f } == true
        val hasEast = sameGroupNeighbor(pos.east())?.let { it.localFillLevel > 0f } == true

        val minX = if (hasWest) 0f else INSET
        val maxX = if (hasEast) 1f else 1f - INSET
        val minY = if (hasDown) 0f else INSET
        val minZ = if (hasNorth) 0f else INSET
        val maxZ = if (hasSouth) 1f else 1f - INSET

        val fluidTop = minY + (1f - INSET - minY) * entity.localFillLevel

        matrices.push()
        val entry = matrices.peek()

        val quality = CTClientConfig.instance.renderQuality
        val worldTime = world?.time?.toFloat() ?: 0f
        val elapsedTicks = worldTime - entity.waveStartTick.toFloat()
        val decayFactor = if (quality == RenderQuality.LOW) 0f else max(0f, 1f - elapsedTicks / DECAY_TICKS)

        val animTime = worldTime + tickDelta
        // gridSize は decayFactor == 0 でも変えない。途中で分割数が変わると UV のちらつきが発生するため。
        val gridSize = if (quality == RenderQuality.HIGH) 8 else 4
        val worldX = pos.x.toFloat()
        val worldZ = pos.z.toFloat()
        val useWorldCoords = quality == RenderQuality.HIGH
        renderAnimatedFluid(
            consumer, entry, sprite, argb, fluidTop, animTime,
            gridSize, useWorldCoords, worldX, worldZ, decayFactor,
            minX, maxX, minY, minZ, maxZ,
            hasUp, hasDown, hasNorth, hasSouth, hasWest, hasEast,
        )

        matrices.pop()
    }

    @Suppress("LongParameterList")
    private fun renderAnimatedFluid(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        sprite: Sprite,
        argb: Int,
        fluidTop: Float,
        animTime: Float,
        gridSize: Int,
        useWorldCoords: Boolean,
        worldX: Float,
        worldZ: Float,
        decayFactor: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        minZ: Float,
        maxZ: Float,
        hasUp: Boolean,
        hasDown: Boolean,
        hasNorth: Boolean,
        hasSouth: Boolean,
        hasWest: Boolean,
        hasEast: Boolean,
    ) {
        val fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE
        val ov = OverlayTexture.DEFAULT_UV
        val u0 = sprite.minU
        val u1 = sprite.maxU
        val v0 = sprite.minV
        val v1 = sprite.maxV
        val maxY = 1f - INSET

        val heightMap = Array(gridSize + 1) { FloatArray(gridSize + 1) }
        val rangeX = maxX - minX
        val rangeZ = maxZ - minZ
        for (ix in 0..gridSize) {
            for (iz in 0..gridSize) {
                val localX = minX + rangeX * ix.toFloat() / gridSize
                val localZ = minZ + rangeZ * iz.toFloat() / gridSize
                val waveX = if (useWorldCoords) worldX + localX else localX
                val waveZ = if (useWorldCoords) worldZ + localZ else localZ
                val wave = decayFactor * (
                    AMPLITUDE * sin(waveX * FREQUENCY + animTime * SPEED) +
                        SECONDARY_AMP * sin(waveZ * SECONDARY_FREQ + animTime * SECONDARY_SPEED)
                    )
                heightMap[ix][iz] = (fluidTop + wave).coerceIn(minY, maxY)
            }
        }

        // Top surface
        if (!hasUp) {
            for (ix in 0 until gridSize) {
                for (iz in 0 until gridSize) {
                    val x0 = minX + rangeX * ix.toFloat() / gridSize
                    val x1g = minX + rangeX * (ix + 1).toFloat() / gridSize
                    val z0 = minZ + rangeZ * iz.toFloat() / gridSize
                    val z1g = minZ + rangeZ * (iz + 1).toFloat() / gridSize

                    val su0 = u0 + (u1 - u0) * ix.toFloat() / gridSize
                    val su1 = u0 + (u1 - u0) * (ix + 1).toFloat() / gridSize
                    val sv0 = v0 + (v1 - v0) * iz.toFloat() / gridSize
                    val sv1 = v0 + (v1 - v0) * (iz + 1).toFloat() / gridSize

                    consumer.quadUV(
                        entry, argb, ov, fullLight,
                        x0, heightMap[ix][iz], z0, su0, sv0,
                        x0, heightMap[ix][iz + 1], z1g, su0, sv1,
                        x1g, heightMap[ix + 1][iz + 1], z1g, su1, sv1,
                        x1g, heightMap[ix + 1][iz], z0, su1, sv0,
                        0f, 1f, 0f,
                    )
                }
            }
        }

        // Bottom surface (flat, no wave)
        if (!hasDown) {
            consumer.quadUV(
                entry, argb, ov, fullLight,
                minX, minY, maxZ, u0, v0,
                minX, minY, minZ, u0, v1,
                maxX, minY, minZ, u1, v1,
                maxX, minY, maxZ, u1, v0,
                0f, -1f, 0f,
            )
        }

        // North side (z = minZ)
        if (!hasNorth) {
            for (ix in 0 until gridSize) {
                val x0 = minX + rangeX * ix.toFloat() / gridSize
                val x1g = minX + rangeX * (ix + 1).toFloat() / gridSize
                val topY0 = heightMap[ix][0]
                val topY1 = heightMap[ix + 1][0]
                val su0 = u0 + (u1 - u0) * ix.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (ix + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry, argb, ov, fullLight,
                    x0, topY0, minZ, su0, v0,
                    x1g, topY1, minZ, su1, v0,
                    x1g, minY, minZ, su1, v1,
                    x0, minY, minZ, su0, v1,
                    0f, 0f, -1f,
                )
            }
        }

        // South side (z = maxZ)
        if (!hasSouth) {
            for (ix in 0 until gridSize) {
                val x0 = minX + rangeX * ix.toFloat() / gridSize
                val x1g = minX + rangeX * (ix + 1).toFloat() / gridSize
                val topY0 = heightMap[ix][gridSize]
                val topY1 = heightMap[ix + 1][gridSize]
                val su0 = u0 + (u1 - u0) * ix.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (ix + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry, argb, ov, fullLight,
                    x1g, topY1, maxZ, su1, v0,
                    x0, topY0, maxZ, su0, v0,
                    x0, minY, maxZ, su0, v1,
                    x1g, minY, maxZ, su1, v1,
                    0f, 0f, 1f,
                )
            }
        }

        // West side (x = minX)
        if (!hasWest) {
            for (iz in 0 until gridSize) {
                val z0 = minZ + rangeZ * iz.toFloat() / gridSize
                val z1g = minZ + rangeZ * (iz + 1).toFloat() / gridSize
                val topY0 = heightMap[0][iz]
                val topY1 = heightMap[0][iz + 1]
                val su0 = u0 + (u1 - u0) * iz.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (iz + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry, argb, ov, fullLight,
                    minX, topY0, z1g, su1, v0,
                    minX, topY1, z0, su0, v0,
                    minX, minY, z0, su0, v1,
                    minX, minY, z1g, su1, v1,
                    -1f, 0f, 0f,
                )
            }
        }

        // East side (x = maxX)
        if (!hasEast) {
            for (iz in 0 until gridSize) {
                val z0 = minZ + rangeZ * iz.toFloat() / gridSize
                val z1g = minZ + rangeZ * (iz + 1).toFloat() / gridSize
                val topY0 = heightMap[gridSize][iz]
                val topY1 = heightMap[gridSize][iz + 1]
                val su0 = u0 + (u1 - u0) * iz.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (iz + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry, argb, ov, fullLight,
                    maxX, topY0, z0, su0, v0,
                    maxX, topY1, z1g, su1, v0,
                    maxX, minY, z1g, su1, v1,
                    maxX, minY, z0, su0, v1,
                    1f, 0f, 0f,
                )
            }
        }
    }

    private fun VertexConsumer.quadUV(
        entry: MatrixStack.Entry,
        argb: Int,
        ov: Int,
        fullLight: Int,
        x1: Float,
        y1: Float,
        z1: Float,
        su0: Float,
        sv0: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        su1: Float,
        sv1: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        su2: Float,
        sv2: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        su3: Float,
        sv3: Float,
        nx: Float,
        ny: Float,
        nz: Float,
    ) {
        vertex(entry, x1, y1, z1).color(argb).texture(su0, sv0).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
        vertex(entry, x2, y2, z2).color(argb).texture(su1, sv1).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
        vertex(entry, x3, y3, z3).color(argb).texture(su2, sv2).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
        vertex(entry, x4, y4, z4).color(argb).texture(su3, sv3).overlay(ov).light(fullLight).normal(entry, nx, ny, nz)
    }
}
