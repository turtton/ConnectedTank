package net.turtton.connectedtank.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.StringReader
import java.nio.file.Files
import net.fabricmc.loader.api.FabricLoader
import net.turtton.connectedtank.ConnectedTank

class CTServerConfig(
    var tankBucketCapacity: Int = DEFAULT_BUCKET_CAPACITY,
) {
    fun save() {
        try {
            val jsonString = GSON.toJson(this)
            Files.writeString(CONFIG_PATH, jsonString)
        } catch (e: Exception) {
            ConnectedTank.logger.error("Failed to save server config", e)
        }
    }

    companion object {
        const val DEFAULT_BUCKET_CAPACITY = 32
        const val MAX_BUCKET_CAPACITY = 256
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_PATH = FabricLoader.getInstance().configDir.resolve("connectedtank-server.json")

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
                instance = CTServerConfig(
                    tankBucketCapacity = capacity.coerceIn(1, MAX_BUCKET_CAPACITY),
                )
            } catch (e: Exception) {
                ConnectedTank.logger.error("Failed to load server config, using defaults", e)
                instance = CTServerConfig()
            }
            instance.save()
        }
    }
}
