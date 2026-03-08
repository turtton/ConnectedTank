package net.turtton.connectedtank.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.turtton.connectedtank.ConnectedTank

class CTClientConfig(
    // TODO: ConnectedTankBlockEntityRenderer で使用予定 (#8)
    var renderQuality: RenderQuality = RenderQuality.MEDIUM,
) {
    fun save() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(this)
        val commented = buildString {
            appendLine("// ConnectedTank Client Config")
            for (line in jsonString.lines()) {
                if (line.contains("\"renderQuality\"")) {
                    appendLine("  // Render quality: LOW, MEDIUM, HIGH")
                }
                appendLine(line)
            }
        }
        java.nio.file.Files.writeString(CONFIG_PATH, commented)
    }

    enum class RenderQuality {
        LOW,
        MEDIUM,
        HIGH,
    }

    companion object {
        private val CONFIG_PATH = FabricLoader.getInstance().configDir.resolve("connectedtank-client.json")

        var instance = CTClientConfig()
            private set

        fun load() {
            if (!java.nio.file.Files.exists(CONFIG_PATH)) {
                instance = CTClientConfig()
                instance.save()
                return
            }
            try {
                val content = java.nio.file.Files.readString(CONFIG_PATH)
                val stripped = content.lines()
                    .filter { !it.trimStart().startsWith("//") }
                    .joinToString("\n")
                val json = JsonParser.parseString(stripped).asJsonObject
                val quality = if (json.has("renderQuality")) {
                    try {
                        RenderQuality.valueOf(json.get("renderQuality").asString)
                    } catch (_: IllegalArgumentException) {
                        RenderQuality.MEDIUM
                    }
                } else {
                    RenderQuality.MEDIUM
                }
                instance = CTClientConfig(renderQuality = quality)
            } catch (e: Exception) {
                ConnectedTank.logger.error("Failed to load client config, using defaults", e)
                instance = CTClientConfig()
            }
            instance.save()
        }
    }
}
