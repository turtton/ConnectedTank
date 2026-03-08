package net.turtton.connectedtank.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class CTConfigScreen : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        createConfigScreen(parent)
    }

    companion object {
        fun createConfigScreen(parent: Screen?): Screen {
            val client = MinecraftClient.getInstance()
            val isExternalServer = client.world != null && !client.isInSingleplayer

            val serverConfig = if (isExternalServer) {
                SyncedServerConfig.syncedConfig ?: CTServerConfig.instance
            } else {
                CTServerConfig.instance
            }

            val tankCapacityOption = Option.createBuilder<Int>()
                .name(Text.translatable("config.connectedtank.server.tankBucketCapacity"))
                .description(
                    OptionDescription.of(
                        Text.translatable("config.connectedtank.server.tankBucketCapacity.description"),
                    ),
                )
                .binding(
                    CTServerConfig.DEFAULT_BUCKET_CAPACITY,
                    { serverConfig.tankBucketCapacity },
                    { serverConfig.tankBucketCapacity = it },
                )
                .controller { option ->
                    IntegerSliderControllerBuilder.create(option)
                        .range(1, 256)
                        .step(1)
                }
                .available(!isExternalServer)
                .build()

            val clientConfig = CTClientConfig.instance

            val renderQualityOption = Option.createBuilder<CTClientConfig.RenderQuality>()
                .name(Text.translatable("config.connectedtank.client.renderQuality"))
                .description(
                    OptionDescription.of(
                        Text.translatable("config.connectedtank.client.renderQuality.description"),
                    ),
                )
                .binding(
                    CTClientConfig.RenderQuality.MEDIUM,
                    { clientConfig.renderQuality },
                    { clientConfig.renderQuality = it },
                )
                .controller { option ->
                    dev.isxander.yacl3.api.controller.EnumControllerBuilder.create(option)
                        .enumClass(CTClientConfig.RenderQuality::class.java)
                }
                .build()

            return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("config.connectedtank.title"))
                .category(
                    ConfigCategory.createBuilder()
                        .name(Text.translatable("config.connectedtank.category.server"))
                        .option(tankCapacityOption)
                        .build(),
                )
                .category(
                    ConfigCategory.createBuilder()
                        .name(Text.translatable("config.connectedtank.category.client"))
                        .option(renderQualityOption)
                        .build(),
                )
                .save {
                    if (!isExternalServer) {
                        CTServerConfig.instance.save()
                    }
                    CTClientConfig.instance.save()
                }
                .build()
                .generateScreen(parent)
        }
    }
}
