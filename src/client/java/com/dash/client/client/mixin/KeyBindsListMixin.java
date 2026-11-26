package com.dash.client.client.mixin;

import com.dash.client.client.SaveMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyBindsList.class)
public class KeyBindsListMixin {
    @Redirect(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping[] dashVisibleBindings(Options options) {
        return SaveMode.visibleBindings(options.keyMappings);
    }
}
