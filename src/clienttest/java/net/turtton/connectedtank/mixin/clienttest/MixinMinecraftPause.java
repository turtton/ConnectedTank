package net.turtton.connectedtank.mixin.clienttest;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// MC >=26.1: pause-on-unfocus moved to Minecraft.pauseIfInactive(). require=0 for <26.1 compat.
@Mixin(Minecraft.class)
public class MixinMinecraftPause {
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true, require = 0)
    private void suppressPauseOnUnfocus(CallbackInfo ci) {
        ci.cancel();
    }
}
