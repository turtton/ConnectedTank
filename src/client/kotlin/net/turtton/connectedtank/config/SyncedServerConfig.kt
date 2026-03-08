package net.turtton.connectedtank.config

object SyncedServerConfig {
    @Volatile
    var syncedConfig: CTServerConfig? = null
}
