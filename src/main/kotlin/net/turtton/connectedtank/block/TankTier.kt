package net.turtton.connectedtank.block

import net.turtton.connectedtank.config.CTServerConfig

enum class TankTier(val id: String, val defaultMultiplier: Int) {
    BASE("connected_tank", 1),
    STONE("stone_connected_tank", 2),
    COPPER("copper_connected_tank", 4),
    IRON("iron_connected_tank", 8),
    GOLD("gold_connected_tank", 16),
    DIAMOND("diamond_connected_tank", 32),
    NETHERITE("netherite_connected_tank", 64),
    ;

    val bucketCapacity: Int
        get() = CTServerConfig.instance.getTierCapacity(this)
}
