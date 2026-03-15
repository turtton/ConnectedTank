package net.turtton.connectedtank.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
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
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"))
    private void connectedtank$onPlaceHead(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        BlockPos hitPos;
        if (context.canReplaceExisting()) {
            hitPos = context.getBlockPos();
        } else {
            hitPos = context.getBlockPos().offset(context.getSide().getOpposite());
        }
        ConnectedTankPlacementContext.INSTANCE.setInteractedAt(hitPos);
    }

    /**
     * place() の RETURN で ThreadLocal をクリアする。
     * 例外時は RETURN に到達しないため、consumeInteractedAt() が唯一のクリーンアップ手段となる。
     * {@link ConnectedTankPlacementContext#consumeInteractedAt()} も参照。
     */
    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN"))
    private void connectedtank$onPlaceReturn(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        ConnectedTankPlacementContext.INSTANCE.clear();
    }
}
