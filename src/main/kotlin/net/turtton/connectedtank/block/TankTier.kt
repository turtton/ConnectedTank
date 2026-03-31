package net.turtton.connectedtank.block

import net.turtton.connectedtank.config.CTServerConfig

enum class TankTier(val id: String, val defaultMultiplier: Int, val hardness: Float) {
    BASE("connected_tank", 1, 0.3f),
    STONE("stone_connected_tank", 2, 0.4f),
    COPPER("copper_connected_tank", 4, 0.5f),
    IRON("iron_connected_tank", 8, 0.6f),
    GOLD("gold_connected_tank", 16, 0.7f),
    DIAMOND("diamond_connected_tank", 32, 0.8f),
    NETHERITE("netherite_connected_tank", 64, 0.9f),
    ;

    val bucketCapacity: Int
        get() = CTServerConfig.instance.getTierCapacity(this)
}
