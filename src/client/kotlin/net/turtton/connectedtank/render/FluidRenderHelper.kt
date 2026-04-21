package net.turtton.connectedtank.render

import kotlin.math.sin
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.texture.Sprite
import net.minecraft.client.util.math.MatrixStack

data class WaveParams(
    val animTime: Float,
    val gridSize: Int,
    val useWorldCoords: Boolean = false,
    val worldX: Float = 0f,
    val worldZ: Float = 0f,
    val decayFactor: Float = 0f,
)

data class NeighborMask(
    val up: Boolean = false,
    val down: Boolean = false,
    val north: Boolean = false,
    val south: Boolean = false,
    val west: Boolean = false,
    val east: Boolean = false,
)

object FluidRenderHelper {
    private const val INSET = 0.001f

    private const val AMPLITUDE = 0.02f
    private const val FREQUENCY = 1.2f
    private const val SPEED = 0.25f
    private const val SECONDARY_AMP = 0.01f
    private const val SECONDARY_FREQ = 1.6f
    private const val SECONDARY_SPEED = 0.18f

    fun renderFluid(
        vertexConsumers: VertexConsumerProvider,
        matrices: MatrixStack,
        sprite: Sprite,
        argb: Int,
        fillLevel: Float,
        wave: WaveParams,
        neighbors: NeighborMask = NeighborMask(),
        renderLayer: RenderLayer = RenderLayer.getEntityTranslucent(sprite.atlasId),
    ) {
        if (fillLevel <= 0f || wave.gridSize <= 0) return

        val minX = if (neighbors.west) 0f else INSET
        val maxX = if (neighbors.east) 1f else 1f - INSET
        val minY = if (neighbors.down) 0f else INSET
        val minZ = if (neighbors.north) 0f else INSET
        val maxZ = if (neighbors.south) 1f else 1f - INSET

        val fluidTop = minY + (1f - INSET - minY) * fillLevel

        val consumer = vertexConsumers.getBuffer(renderLayer)
        val entry = matrices.peek()

        renderAnimatedFluid(
            consumer,
            entry,
            sprite,
            argb,
            fluidTop,
            wave,
            minX,
            maxX,
            minY,
            minZ,
            maxZ,
            neighbors,
        )
    }

    @Suppress("LongParameterList")
    private fun renderAnimatedFluid(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        sprite: Sprite,
        argb: Int,
        fluidTop: Float,
        wave: WaveParams,
        minX: Float,
        maxX: Float,
        minY: Float,
        minZ: Float,
        maxZ: Float,
        neighbors: NeighborMask,
    ) {
        val fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE
        val ov = OverlayTexture.DEFAULT_UV
        val u0 = sprite.minU
        val u1 = sprite.maxU
        val v0 = sprite.minV
        val v1 = sprite.maxV
        val maxY = 1f - INSET

        val gridSize = wave.gridSize
        val heightMap = Array(gridSize + 1) { FloatArray(gridSize + 1) }
        val rangeX = maxX - minX
        val rangeZ = maxZ - minZ
        for (ix in 0..gridSize) {
            for (iz in 0..gridSize) {
                val localX = minX + rangeX * ix.toFloat() / gridSize
                val localZ = minZ + rangeZ * iz.toFloat() / gridSize
                val waveX = if (wave.useWorldCoords) wave.worldX + localX else localX
                val waveZ = if (wave.useWorldCoords) wave.worldZ + localZ else localZ
                val w =
                    wave.decayFactor * (
                        AMPLITUDE * sin(waveX * FREQUENCY + wave.animTime * SPEED) +
                            SECONDARY_AMP * sin(waveZ * SECONDARY_FREQ + wave.animTime * SECONDARY_SPEED)
                        )
                heightMap[ix][iz] = (fluidTop + w).coerceIn(minY, maxY)
            }
        }

        // Top surface
        if (!neighbors.up) {
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
                        entry,
                        argb,
                        ov,
                        fullLight,
                        x0,
                        heightMap[ix][iz],
                        z0,
                        su0,
                        sv0,
                        x0,
                        heightMap[ix][iz + 1],
                        z1g,
                        su0,
                        sv1,
                        x1g,
                        heightMap[ix + 1][iz + 1],
                        z1g,
                        su1,
                        sv1,
                        x1g,
                        heightMap[ix + 1][iz],
                        z0,
                        su1,
                        sv0,
                        0f,
                        1f,
                        0f,
                    )
                }
            }
        }

        // Bottom surface (flat, no wave)
        if (!neighbors.down) {
            consumer.quadUV(
                entry,
                argb,
                ov,
                fullLight,
                minX,
                minY,
                maxZ,
                u0,
                v0,
                minX,
                minY,
                minZ,
                u0,
                v1,
                maxX,
                minY,
                minZ,
                u1,
                v1,
                maxX,
                minY,
                maxZ,
                u1,
                v0,
                0f,
                -1f,
                0f,
            )
        }

        // North side (z = minZ)
        if (!neighbors.north) {
            for (ix in 0 until gridSize) {
                val x0 = minX + rangeX * ix.toFloat() / gridSize
                val x1g = minX + rangeX * (ix + 1).toFloat() / gridSize
                val topY0 = heightMap[ix][0]
                val topY1 = heightMap[ix + 1][0]
                val su0 = u0 + (u1 - u0) * ix.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (ix + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry,
                    argb,
                    ov,
                    fullLight,
                    x0,
                    topY0,
                    minZ,
                    su0,
                    v0,
                    x1g,
                    topY1,
                    minZ,
                    su1,
                    v0,
                    x1g,
                    minY,
                    minZ,
                    su1,
                    v1,
                    x0,
                    minY,
                    minZ,
                    su0,
                    v1,
                    0f,
                    0f,
                    -1f,
                )
            }
        }

        // South side (z = maxZ)
        if (!neighbors.south) {
            for (ix in 0 until gridSize) {
                val x0 = minX + rangeX * ix.toFloat() / gridSize
                val x1g = minX + rangeX * (ix + 1).toFloat() / gridSize
                val topY0 = heightMap[ix][gridSize]
                val topY1 = heightMap[ix + 1][gridSize]
                val su0 = u0 + (u1 - u0) * ix.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (ix + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry,
                    argb,
                    ov,
                    fullLight,
                    x1g,
                    topY1,
                    maxZ,
                    su1,
                    v0,
                    x0,
                    topY0,
                    maxZ,
                    su0,
                    v0,
                    x0,
                    minY,
                    maxZ,
                    su0,
                    v1,
                    x1g,
                    minY,
                    maxZ,
                    su1,
                    v1,
                    0f,
                    0f,
                    1f,
                )
            }
        }

        // West side (x = minX)
        if (!neighbors.west) {
            for (iz in 0 until gridSize) {
                val z0 = minZ + rangeZ * iz.toFloat() / gridSize
                val z1g = minZ + rangeZ * (iz + 1).toFloat() / gridSize
                val topY0 = heightMap[0][iz]
                val topY1 = heightMap[0][iz + 1]
                val su0 = u0 + (u1 - u0) * iz.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (iz + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry,
                    argb,
                    ov,
                    fullLight,
                    minX,
                    topY0,
                    z1g,
                    su1,
                    v0,
                    minX,
                    topY1,
                    z0,
                    su0,
                    v0,
                    minX,
                    minY,
                    z0,
                    su0,
                    v1,
                    minX,
                    minY,
                    z1g,
                    su1,
                    v1,
                    -1f,
                    0f,
                    0f,
                )
            }
        }

        // East side (x = maxX)
        if (!neighbors.east) {
            for (iz in 0 until gridSize) {
                val z0 = minZ + rangeZ * iz.toFloat() / gridSize
                val z1g = minZ + rangeZ * (iz + 1).toFloat() / gridSize
                val topY0 = heightMap[gridSize][iz]
                val topY1 = heightMap[gridSize][iz + 1]
                val su0 = u0 + (u1 - u0) * iz.toFloat() / gridSize
                val su1 = u0 + (u1 - u0) * (iz + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry,
                    argb,
                    ov,
                    fullLight,
                    maxX,
                    topY0,
                    z0,
                    su0,
                    v0,
                    maxX,
                    topY1,
                    z1g,
                    su1,
                    v0,
                    maxX,
                    minY,
                    z1g,
                    su1,
                    v1,
                    maxX,
                    minY,
                    z0,
                    su0,
                    v1,
                    1f,
                    0f,
                    0f,
                )
            }
        }
    }

    @Suppress("LongParameterList")
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
        vertex(entry, x1, y1, z1).color(argb).texture(su0, sv0).overlay(ov).light(fullLight)
            .normal(entry, nx, ny, nz)
        vertex(entry, x2, y2, z2).color(argb).texture(su1, sv1).overlay(ov).light(fullLight)
            .normal(entry, nx, ny, nz)
        vertex(entry, x3, y3, z3).color(argb).texture(su2, sv2).overlay(ov).light(fullLight)
            .normal(entry, nx, ny, nz)
        vertex(entry, x4, y4, z4).color(argb).texture(su3, sv3).overlay(ov).light(fullLight)
            .normal(entry, nx, ny, nz)
    }
}
