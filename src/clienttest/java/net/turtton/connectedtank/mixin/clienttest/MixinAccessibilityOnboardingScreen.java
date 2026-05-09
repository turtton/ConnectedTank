package net.turtton.connectedtank.mixin.clienttest;

import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Xvfb: Narrator subsystem unavailable. Skip getNarrator(), run callback directly.
@Mixin(AccessibilityOnboardingScreen.class)
public class MixinAccessibilityOnboardingScreen {
    @Inject(
            method = "close",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"),
            cancellable = true)
    private void suppressNarratorError(boolean dontShowAgain, Runnable callback, CallbackInfo ci) {
        callback.run();
        ci.cancel();
    }
}
