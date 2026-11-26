package com.dash.client.client.mixin;

import com.dash.client.client.SaveMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.basefinder.FreecamManager", remap = false)
abstract class StandaloneFreecamMixin {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private static void dash$hidePausedFreecam(CallbackInfoReturnable<Boolean> cir) {
        if (SaveMode.isPaused()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void dash$pauseMovement(CallbackInfo ci) {
        if (SaveMode.isPaused()) {
            ci.cancel();
        }
    }
}
