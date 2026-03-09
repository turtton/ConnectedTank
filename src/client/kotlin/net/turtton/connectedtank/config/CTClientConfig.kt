package net.turtton.connectedtank.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.StringReader
import java.nio.file.Files
import net.fabricmc.loader.api.FabricLoader
import net.turtton.connectedtank.ConnectedTank

class CTClientConfig(
    // TODO: ConnectedTankBlockEntityRenderer で使用予定 (#8)
    var renderQuality: RenderQuality = RenderQuality.MEDIUM,
) {
    fun save() {
        try {
            Files.createDirectories(CONFIG_DIR)
            val jsonString = GSON.toJson(this)
            Files.writeString(CONFIG_PATH, jsonString)
        } catch (e: Exception) {
            ConnectedTank.logger.error("Failed to save client config", e)
        }
    }

    enum class RenderQuality {
        LOW,
        MEDIUM,
        HIGH,
    }

    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_DIR = FabricLoader.getInstance().configDir.resolve("connectedtank")
        private val CONFIG_PATH = CONFIG_DIR.resolve("client.json")

        @Volatile
        var instance = CTClientConfig()
            private set

        fun load() {
            if (!Files.exists(CONFIG_PATH)) {
                instance = CTClientConfig()
                instance.save()
                return
            }
            try {
                val content = Files.readString(CONFIG_PATH)
                val reader = JsonReader(StringReader(content))
                reader.isLenient = true
                val json = JsonParser.parseReader(reader).asJsonObject
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
