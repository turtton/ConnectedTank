package net.turtton.connectedtank.block

import kotlin.math.max
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering
//? if >=1.21.11 {
/*import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.Minecraft
//?} else {
import net.minecraft.client.renderer.state.CameraRenderState
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTClientConfig.RenderQuality
import net.turtton.connectedtank.render.FluidRenderHelper
import net.turtton.connectedtank.render.NeighborMask
import net.turtton.connectedtank.render.WaveParams

class ConnectedTankRenderState : BlockEntityRenderState() {
    var sprite: TextureAtlasSprite? = null
    var argb: Int = 0
    var localFillLevel: Float = 0f
    var wave: WaveParams = WaveParams(animTime = 0f, gridSize = 4)
    var neighbors: NeighborMask = NeighborMask()
}

class ConnectedTankBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context,
) : BlockEntityRenderer<ConnectedTankBlockEntity, ConnectedTankRenderState> {
    companion object {
        private const val DECAY_TICKS = 60f
    }

    override fun createRenderState(): ConnectedTankRenderState = ConnectedTankRenderState()

    override fun extractRenderState(
        entity: ConnectedTankBlockEntity,
        state: ConnectedTankRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumbling: ModelFeatureRenderer.CrumblingOverlay?,
    ) {
        super.extractRenderState(entity, state, tickDelta, cameraPos, crumbling)

        state.localFillLevel = entity.localFillLevel
        if (entity.localFillLevel <= 0f || entity.fluidVariant.isBlank) {
            state.sprite = null
            return
        }

        //? if >=26.1 {
        val fluidModels = Minecraft.getInstance().modelManager.fluidStateModelSet
        state.sprite = fluidModels.get(entity.fluidVariant.fluid.defaultFluidState()).stillMaterial().sprite()
        //?} else {
        state.sprite = FluidVariantRendering.getSprite(entity.fluidVariant)
        //?}
        val color = FluidVariantRendering.getColor(entity.fluidVariant)
        state.argb = (0xFF shl 24) or (color and 0x00FFFFFF)

        val world = entity.level
        val pos = entity.blockPos
        val myGroupId = entity.groupId

        fun sameGroupNeighbor(neighborPos: BlockPos): ConnectedTankBlockEntity? {
            if (myGroupId == null) return null
            val neighbor = world?.getBlockEntity(neighborPos) as? ConnectedTankBlockEntity ?: return null
            return if (neighbor.groupId == myGroupId) neighbor else null
        }

        val neighborDown = sameGroupNeighbor(pos.below())
        val neighborUp = sameGroupNeighbor(pos.above())

        // 垂直: 下タンクが満杯で同一グループのときのみ連続とみなす
        val hasDown = neighborDown != null && neighborDown.localFillLevel >= 1.0f
        // 垂直: 自身が満杯かつ上タンクに同一グループの液体があるときのみ上面を省略
        val hasUp = neighborUp != null && entity.localFillLevel >= 1.0f && neighborUp.localFillLevel > 0f
        // 水平: 同一グループで液体が存在する隣接タンクのみ連続
        val hasNorth = sameGroupNeighbor(pos.north())?.let { it.localFillLevel > 0f } == true
        val hasSouth = sameGroupNeighbor(pos.south())?.let { it.localFillLevel > 0f } == true
        val hasWest = sameGroupNeighbor(pos.west())?.let { it.localFillLevel > 0f } == true
        val hasEast = sameGroupNeighbor(pos.east())?.let { it.localFillLevel > 0f } == true

        state.neighbors = NeighborMask(
            up = hasUp,
            down = hasDown,
            north = hasNorth,
            south = hasSouth,
            west = hasWest,
            east = hasEast,
        )

        val quality = CTClientConfig.instance.renderQuality
        val worldTime = world?.gameTime?.toFloat() ?: 0f
        val elapsedTicks = worldTime - entity.waveStartTick.toFloat()
        val decayFactor = if (quality == RenderQuality.LOW) 0f else max(0f, 1f - elapsedTicks / DECAY_TICKS)
        val animTime = worldTime + tickDelta
        // gridSize は decayFactor == 0 でも変えない。途中で分割数が変わると UV のちらつきが発生するため。
        val gridSize = if (quality == RenderQuality.HIGH) 8 else 4
        val worldX = pos.x.toFloat()
        val worldZ = pos.z.toFloat()
        val useWorldCoords = quality == RenderQuality.HIGH

        state.wave = WaveParams(
            animTime = animTime,
            gridSize = gridSize,
            useWorldCoords = useWorldCoords,
            worldX = worldX,
            worldZ = worldZ,
            decayFactor = decayFactor,
        )
    }

    override fun submit(
        state: ConnectedTankRenderState,
        matrices: PoseStack,
        queue: SubmitNodeCollector,
        cameraState: CameraRenderState,
    ) {
        val sprite = state.sprite ?: return
        if (state.localFillLevel <= 0f) return

        matrices.pushPose()
        FluidRenderHelper.renderFluid(
            queue,
            matrices,
            sprite,
            state.argb,
            state.localFillLevel,
            state.wave,
            state.neighbors,
        )
        matrices.popPose()
    }
}
*/
//?} else {
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.turtton.connectedtank.config.CTClientConfig
import net.turtton.connectedtank.config.CTClientConfig.RenderQuality
import net.turtton.connectedtank.render.FluidRenderHelper
import net.turtton.connectedtank.render.NeighborMask
import net.turtton.connectedtank.render.WaveParams

class ConnectedTankBlockEntityRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context,
) : BlockEntityRenderer<ConnectedTankBlockEntity> {
    companion object {
        private const val DECAY_TICKS = 60f
    }

    override fun render(
        entity: ConnectedTankBlockEntity,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        cameraPos: Vec3,
    ) {
        if (entity.localFillLevel <= 0f || entity.fluidVariant.isBlank) return

        val sprite = FluidVariantRendering.getSprite(entity.fluidVariant) ?: return
        val color = FluidVariantRendering.getColor(entity.fluidVariant)
        val argb = (0xFF shl 24) or (color and 0x00FFFFFF)

        val world = entity.level
        val pos = entity.blockPos

        val myGroupId = entity.groupId
        fun sameGroupNeighbor(neighborPos: BlockPos): ConnectedTankBlockEntity? {
            if (myGroupId == null) return null
            val neighbor = world?.getBlockEntity(neighborPos) as? ConnectedTankBlockEntity ?: return null
            return if (neighbor.groupId == myGroupId) neighbor else null
        }

        val neighborDown = sameGroupNeighbor(pos.below())
        val neighborUp = sameGroupNeighbor(pos.above())

        // 垂直: 下タンクが満杯で同一グループのときのみ連続とみなす
        val hasDown = neighborDown != null && neighborDown.localFillLevel >= 1.0f
        // 垂直: 自身が満杯かつ上タンクに同一グループの液体があるときのみ上面を省略
        val hasUp = neighborUp != null && entity.localFillLevel >= 1.0f && neighborUp.localFillLevel > 0f
        // 水平: 同一グループで液体が存在する隣接タンクのみ連続
        val hasNorth = sameGroupNeighbor(pos.north())?.let { it.localFillLevel > 0f } == true
        val hasSouth = sameGroupNeighbor(pos.south())?.let { it.localFillLevel > 0f } == true
        val hasWest = sameGroupNeighbor(pos.west())?.let { it.localFillLevel > 0f } == true
        val hasEast = sameGroupNeighbor(pos.east())?.let { it.localFillLevel > 0f } == true

        matrices.pushPose()

        val quality = CTClientConfig.instance.renderQuality
        val worldTime = world?.gameTime?.toFloat() ?: 0f
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

        matrices.popPose()
    }
}
//?}
