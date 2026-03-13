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
        if (entity.fillLevel <= 0f || entity.fluidVariant.isBlank) return

        val sprite = FluidVariantRendering.getSprite(entity.fluidVariant) ?: return
        val color = FluidVariantRendering.getColor(entity.fluidVariant)
        val argb = (0xFF shl 24) or (color and 0x00FFFFFF)

        val renderLayer = RenderLayer.getEntityTranslucent(sprite.atlasId)
        val consumer = vertexConsumers.getBuffer(renderLayer)

        val world = entity.world
        val pos = entity.pos

        val hasDown = world?.getBlockState(pos.down())?.block?.let(CTBlocks::isConnectedTank) == true
        val hasUp = world?.getBlockState(pos.up())?.block?.let(CTBlocks::isConnectedTank) == true
        val hasNorth = world?.getBlockState(pos.north())?.block?.let(CTBlocks::isConnectedTank) == true
        val hasSouth = world?.getBlockState(pos.south())?.block?.let(CTBlocks::isConnectedTank) == true
        val hasWest = world?.getBlockState(pos.west())?.block?.let(CTBlocks::isConnectedTank) == true
        val hasEast = world?.getBlockState(pos.east())?.block?.let(CTBlocks::isConnectedTank) == true

        val minX = if (hasWest) 0f else INSET
        val maxX = if (hasEast) 1f else 1f - INSET
        val minY = if (hasDown) 0f else INSET
        val minZ = if (hasNorth) 0f else INSET
        val maxZ = if (hasSouth) 1f else 1f - INSET

        val fluidTop = run {
            if (world == null) return@run minY + (1f - INSET - minY) * entity.fillLevel

            var blocksBelow = 0
            var p = pos.down()
            while (CTBlocks.isConnectedTank(world.getBlockState(p).block)) {
                blocksBelow++
                p = p.down()
            }
            var blocksAbove = 0
            p = pos.up()
            while (CTBlocks.isConnectedTank(world.getBlockState(p).block)) {
                blocksAbove++
                p = p.up()
            }
            val columnHeight = 1 + blocksBelow + blocksAbove
            if (columnHeight <= 1) {
                return@run minY + (1f - INSET - minY) * entity.fillLevel
            }
            val totalFillHeight = entity.fillLevel * columnHeight
            val lowerBound = blocksBelow.toFloat()
            if (totalFillHeight <= lowerBound) return
            val localFill = ((totalFillHeight - lowerBound).coerceIn(0f, 1f))
            minY + (1f - INSET - minY) * localFill
        }

        matrices.push()
        val entry = matrices.peek()

        val quality = CTClientConfig.instance.renderQuality
        val worldTime = world?.time?.toFloat() ?: 0f
        val elapsedTicks = worldTime - entity.waveStartTick.toFloat()
        val decayFactor = if (quality == RenderQuality.LOW) 0f else max(0f, 1f - elapsedTicks / DECAY_TICKS)

        if (decayFactor <= 0f) {
            renderStaticFluid(
                consumer, entry, sprite, argb, fluidTop,
                minX, maxX, minY, minZ, maxZ,
                hasUp, hasDown, hasNorth, hasSouth, hasWest, hasEast,
            )
        } else {
            val animTime = worldTime + tickDelta
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
        }

        matrices.pop()
    }

    private fun renderStaticFluid(
        consumer: VertexConsumer,
        entry: MatrixStack.Entry,
        sprite: Sprite,
        argb: Int,
        fluidTop: Float,
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
                val sv0 = v0 + (v1 - v0) * iz.toFloat() / gridSize
                val sv1 = v0 + (v1 - v0) * (iz + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry, argb, ov, fullLight,
                    minX, topY0, z1g, sv1, v0,
                    minX, topY1, z0, sv0, v0,
                    minX, minY, z0, sv0, v1,
                    minX, minY, z1g, sv1, v1,
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
                val sv0 = v0 + (v1 - v0) * iz.toFloat() / gridSize
                val sv1 = v0 + (v1 - v0) * (iz + 1).toFloat() / gridSize

                consumer.quadUV(
                    entry, argb, ov, fullLight,
                    maxX, topY0, z0, sv0, v0,
                    maxX, topY1, z1g, sv1, v0,
                    maxX, minY, z1g, sv1, v1,
                    maxX, minY, z0, sv0, v1,
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
