package com.dash.client.client.mixin;

import com.dash.client.client.SaveMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.basefinder.BaseFinderClient", remap = false)
abstract class StandaloneBaseFinderMixin {
    @Inject(method = {"renderEsp", "lambda$onInitializeClient$10"}, at = @At("HEAD"), cancellable = true)
    private void dash$pauseRendering(CallbackInfo ci) {
        if (SaveMode.isPaused()) {
            ci.cancel();
        }
    }

    @Inject(method = {"lambda$onInitializeClient$0", "lambda$onInitializeClient$1", "lambda$onInitializeClient$3",
            "setEspEnabled", "setFreecamEnabled"}, at = @At("HEAD"), cancellable = true)
    private static void dash$pauseControls(CallbackInfo ci) {
        if (SaveMode.isPaused()) {
            ci.cancel();
        }
    }
}
