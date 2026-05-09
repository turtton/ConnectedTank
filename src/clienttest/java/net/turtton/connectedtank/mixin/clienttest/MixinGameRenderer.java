package net.turtton.connectedtank.mixin.clienttest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// MC <26.1: pause-on-unfocus is in GameRenderer.render(). require=0 for >=26.1 compat.
@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z"),
            require = 0)
    private boolean ignoreWindowFocus(Minecraft instance) {
        return true;
    }
}
