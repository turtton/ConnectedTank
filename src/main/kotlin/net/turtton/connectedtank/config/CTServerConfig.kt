package net.turtton.connectedtank.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.nio.file.Files
import net.fabricmc.loader.api.FabricLoader
import net.turtton.connectedtank.ConnectedTank

class CTServerConfig(
    var tankBucketCapacity: Int = DEFAULT_BUCKET_CAPACITY,
) {
    fun save() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(this)
        val commented = buildString {
            appendLine("// ConnectedTank Server Config")
            for (line in jsonString.lines()) {
                if (line.contains("\"tankBucketCapacity\"")) {
                    appendLine("  // Bucket capacity per single tank block (default: $DEFAULT_BUCKET_CAPACITY)")
                }
                appendLine(line)
            }
        }
        Files.writeString(CONFIG_PATH, commented)
    }

    companion object {
        const val DEFAULT_BUCKET_CAPACITY = 32
        private val CONFIG_PATH = FabricLoader.getInstance().configDir.resolve("connectedtank-server.json")

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
                val stripped = content.lines()
                    .filter { !it.trimStart().startsWith("//") }
                    .joinToString("\n")
                val json = JsonParser.parseString(stripped).asJsonObject
                val capacity = if (json.has("tankBucketCapacity")) {
                    json.get("tankBucketCapacity").asInt
                } else {
                    DEFAULT_BUCKET_CAPACITY
                }
                instance = CTServerConfig(tankBucketCapacity = capacity.coerceAtLeast(1))
            } catch (e: Exception) {
                ConnectedTank.logger.error("Failed to load server config, using defaults", e)
                instance = CTServerConfig()
            }
            instance.save()
        }
    }
}
