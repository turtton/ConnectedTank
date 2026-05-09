package net.turtton.connectedtank.block

import net.minecraft.core.BlockPos

object ConnectedTankPlacementContext {
    private val interactedAt = ThreadLocal<BlockPos?>()
    private val sneaking = ThreadLocal<Boolean>()

    fun setInteractedAt(pos: BlockPos) {
        interactedAt.set(pos)
    }

    fun setSneaking(value: Boolean) {
        sneaking.set(value)
    }

    /**
     * interactedAt を取得して ThreadLocal から削除する。
     * Mixin の RETURN inject が例外で到達しない場合、この呼び出しが唯一のクリーンアップ手段となる。
     */
    fun consumeInteractedAt(): BlockPos? = interactedAt.get()?.also { interactedAt.remove() }

    fun consumeSneaking(): Boolean = sneaking.get() ?: false

    fun clear() {
        interactedAt.remove()
        sneaking.remove()
    }
}
