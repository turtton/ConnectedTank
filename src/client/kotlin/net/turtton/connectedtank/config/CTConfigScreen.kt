package net.turtton.connectedtank.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screen.Screen
import net.turtton.connectedtank.ConnectedTank

class CTConfigScreen : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        try {
            CTConfigScreenBuilder.createConfigScreen(parent)
        } catch (_: NoClassDefFoundError) {
            ConnectedTank.logger.warn("YACL is not installed, config screen is unavailable")
            parent
        }
    }
}

private object CTConfigScreenBuilder {
    fun createConfigScreen(parent: Screen?): Screen {
        val client = net.minecraft.client.MinecraftClient.getInstance()
        val isExternalServer = client.world != null && !client.isInSingleplayer

        val serverConfig = if (isExternalServer) {
            SyncedServerConfig.syncedConfig ?: CTServerConfig.instance
        } else {
            CTServerConfig.instance
        }

        val tankCapacityOption = dev.isxander.yacl3.api.Option.createBuilder<Int>()
            .name(net.minecraft.text.Text.translatable("config.connectedtank.server.tankBucketCapacity"))
            .description(
                dev.isxander.yacl3.api.OptionDescription.of(
                    net.minecraft.text.Text.translatable("config.connectedtank.server.tankBucketCapacity.description"),
                ),
            )
            .binding(
                CTServerConfig.DEFAULT_BUCKET_CAPACITY,
                { serverConfig.tankBucketCapacity },
                { if (!isExternalServer) serverConfig.tankBucketCapacity = it },
            )
            .controller { option ->
                dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder.create(option)
                    .range(1, CTServerConfig.MAX_BUCKET_CAPACITY)
                    .step(1)
            }
            .available(!isExternalServer)
            .build()

        val clientConfig = CTClientConfig.instance

        val renderQualityOption = dev.isxander.yacl3.api.Option.createBuilder<CTClientConfig.RenderQuality>()
            .name(net.minecraft.text.Text.translatable("config.connectedtank.client.renderQuality"))
            .description(
                dev.isxander.yacl3.api.OptionDescription.of(
                    net.minecraft.text.Text.translatable("config.connectedtank.client.renderQuality.description"),
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

        return dev.isxander.yacl3.api.YetAnotherConfigLib.createBuilder()
            .title(net.minecraft.text.Text.translatable("config.connectedtank.title"))
            .category(
                dev.isxander.yacl3.api.ConfigCategory.createBuilder()
                    .name(net.minecraft.text.Text.translatable("config.connectedtank.category.server"))
                    .option(tankCapacityOption)
                    .build(),
            )
            .category(
                dev.isxander.yacl3.api.ConfigCategory.createBuilder()
                    .name(net.minecraft.text.Text.translatable("config.connectedtank.category.client"))
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
