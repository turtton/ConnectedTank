package net.turtton.connectedtank.block

import net.minecraft.util.math.BlockPos

object ConnectedTankPlacementContext {
    private val interactedAt = ThreadLocal<BlockPos?>()

    fun setInteractedAt(pos: BlockPos) {
        interactedAt.set(pos)
    }

    fun consumeInteractedAt(): BlockPos? = interactedAt.get()?.also { interactedAt.remove() }

    fun clear() {
        interactedAt.remove()
    }
}
