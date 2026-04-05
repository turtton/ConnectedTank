package net.turtton.connectedtank.block

import kotlin.math.max
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTClientConfig.RenderQuality
import net.turtton.connectedtank.render.FluidRenderHelper
import net.turtton.connectedtank.render.NeighborMask
import net.turtton.connectedtank.render.WaveParams

class ConnectedTankBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererFactory.Context,
) : BlockEntityRenderer<ConnectedTankBlockEntity> {
    companion object {
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

        matrices.push()

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

        FluidRenderHelper.renderFluid(
            vertexConsumers,
            matrices,
            sprite,
            argb,
            entity.localFillLevel,
            WaveParams(
                animTime = animTime,
                gridSize = gridSize,
                useWorldCoords = useWorldCoords,
                worldX = worldX,
                worldZ = worldZ,
                decayFactor = decayFactor,
            ),
            NeighborMask(
                up = hasUp,
                down = hasDown,
                north = hasNorth,
                south = hasSouth,
                west = hasWest,
                east = hasEast,
            ),
        )

        matrices.pop()
    }
}
