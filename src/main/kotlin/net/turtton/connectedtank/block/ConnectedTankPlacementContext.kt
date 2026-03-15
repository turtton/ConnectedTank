package net.turtton.connectedtank.block

import net.minecraft.util.math.BlockPos

object ConnectedTankPlacementContext {
    private val interactedAt = ThreadLocal<BlockPos?>()

    fun setInteractedAt(pos: BlockPos) {
        interactedAt.set(pos)
    }

    /**
     * interactedAt を取得して ThreadLocal から削除する。
     * Mixin の RETURN inject が例外で到達しない場合、この呼び出しが唯一のクリーンアップ手段となる。
     */
    fun consumeInteractedAt(): BlockPos? = interactedAt.get()?.also { interactedAt.remove() }

    fun clear() {
        interactedAt.remove()
    }
}
