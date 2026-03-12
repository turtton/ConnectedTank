package net.turtton.connectedtank.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.StringReader
import java.nio.file.Files
import net.fabricmc.loader.api.FabricLoader
import net.turtton.connectedtank.ConnectedTank
import net.turtton.connectedtank.block.TankTier

class CTServerConfig(
    var tankBucketCapacity: Int = DEFAULT_BUCKET_CAPACITY,
    var tierMultipliers: MutableMap<String, Int> = DEFAULT_TIER_MULTIPLIERS.toMutableMap(),
) {
    fun getTierCapacity(tier: TankTier): Int = tankBucketCapacity * tierMultipliers.getOrDefault(tier.name, tier.defaultMultiplier)

    fun save() {
        try {
            Files.createDirectories(CONFIG_DIR)
            val jsonString = GSON.toJson(this)
            Files.writeString(CONFIG_PATH, jsonString)
        } catch (e: Exception) {
            ConnectedTank.logger.error("Failed to save server config", e)
        }
    }

    companion object {
        const val DEFAULT_BUCKET_CAPACITY = 32
        const val MAX_BUCKET_CAPACITY = 256
        val DEFAULT_TIER_MULTIPLIERS: Map<String, Int> = TankTier.entries.associate { it.name to it.defaultMultiplier }
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_DIR = FabricLoader.getInstance().configDir.resolve("connectedtank")
        private val CONFIG_PATH = CONFIG_DIR.resolve("server.json")

        @Volatile
        var instance = CTServerConfig()
            private set

        fun load() {
            if (!Files.exists(CONFIG_PATH)) {
                instance = CTServerConfig()
                instance.save()
                return
            }
            try {
                val content = Files.readString(CONFIG_PATH)
                val reader = JsonReader(StringReader(content))
                reader.isLenient = true
                val json = JsonParser.parseReader(reader).asJsonObject
                val capacity = if (json.has("tankBucketCapacity")) {
                    json.get("tankBucketCapacity").asInt
                } else {
                    DEFAULT_BUCKET_CAPACITY
                }
                val multipliers = if (json.has("tierMultipliers")) {
                    val type = object : TypeToken<Map<String, Int>>() {}.type
                    GSON.fromJson<Map<String, Int>>(json.get("tierMultipliers"), type).toMutableMap()
                } else {
                    DEFAULT_TIER_MULTIPLIERS.toMutableMap()
                }
                instance = CTServerConfig(
                    tankBucketCapacity = capacity.coerceIn(1, MAX_BUCKET_CAPACITY),
                    tierMultipliers = multipliers,
                )
                instance.save()
            } catch (e: Exception) {
                ConnectedTank.logger.error("Failed to load server config, using defaults", e)
                instance = CTServerConfig()
            }
        }
    }
}
