package com.dash.client.client.mixin;

import com.dash.client.client.SaveMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.RealDoggoHitman.nightvisiontoggle.client.NightvisiontoggleClient", remap = false)
abstract class StandaloneNightVisionMixin {
    @Unique
    private MobEffectInstance dash$ownedNightVision;

    @Inject(method = "applyNightVision", at = @At("TAIL"))
    private void dash$recordNightVision(LocalPlayer player, CallbackInfo ci) {
        // The standalone mod only applies Night Vision when the slot is empty.
        dash$ownedNightVision = player.getEffect(MobEffects.NIGHT_VISION);
    }

    @Inject(method = "lambda$onInitializeClient$0", at = @At("HEAD"), cancellable = true)
    private void dash$pauseNightVision(Minecraft client, CallbackInfo ci) {
        if (!SaveMode.isPaused()) {
            return;
        }

        if (dash$ownedNightVision != null && client.player != null
                && client.player.getEffect(MobEffects.NIGHT_VISION) == dash$ownedNightVision) {
            client.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        dash$ownedNightVision = null;
        ci.cancel();
    }
}
