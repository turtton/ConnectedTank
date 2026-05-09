package net.turtton.connectedtank.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.turtton.connectedtank.block.ConnectedTankPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {
    /**
     * place() の先頭でクリック先座標を記録する。
     * canReplaceExisting() == true: getBlockPos() がクリック先そのもの。
     * canReplaceExisting() == false: getBlockPos() は設置先（空きブロック）を返すため、
     * getSide().getOpposite() でオフセットしてクリック先の既存ブロック座標を得る。
     */
    @Inject(
            method =
                    "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void connectedtank$onPlaceHead(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos hitPos;
        if (context.replacingClickedOnBlock()) {
            hitPos = context.getClickedPos();
        } else {
            hitPos = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        }
        ConnectedTankPlacementContext.INSTANCE.setInteractedAt(hitPos);
        var player = context.getPlayer();
        ConnectedTankPlacementContext.INSTANCE.setSneaking(player != null && player.isSecondaryUseActive());
    }

    /**
     * place() の RETURN で ThreadLocal をクリアする。
     * 例外時は RETURN に到達しないため、consumeInteractedAt() が唯一のクリーンアップ手段となる。
     * {@link ConnectedTankPlacementContext#consumeInteractedAt()} も参照。
     */
    @Inject(
            method =
                    "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void connectedtank$onPlaceReturn(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ConnectedTankPlacementContext.INSTANCE.clear();
    }
}
