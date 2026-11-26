package com.dash.client.client;

import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public final class SaveMode {
    private static boolean paused;
    private static KeyMapping[] hiddenBindings = {};
    private static KeyMapping[] visibleBindings;

    private SaveMode() {}

    static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (paused && command.equals("save")) {
                resume(Minecraft.getInstance());
                return false;
            }
            return true;
        });
    }

    public static boolean isPaused() {
        return paused;
    }

    static void pause(Minecraft client) {
        if (paused) {
            return;
        }

        KeyMapping[] bindings = client.options.keyMappings;
        hiddenBindings = Arrays.stream(bindings).filter(SaveMode::isManagedBinding).toArray(KeyMapping[]::new);
        visibleBindings = Arrays.stream(bindings).filter(key -> !isManagedBinding(key)).toArray(KeyMapping[]::new);
        paused = true;
        discardBindingInput();
        DashClientClient.onPause(client);
        client.gui.getChat().rescaleChat();
        client.setScreen(null);
    }

    private static void resume(Minecraft client) {
        discardBindingInput();
        paused = false;
        hiddenBindings = new KeyMapping[0];
        visibleBindings = null;
        client.gui.getChat().rescaleChat();
    }

    public static KeyMapping[] visibleBindings(KeyMapping[] bindings) {
        return paused ? visibleBindings : bindings;
    }

    static void discardBindingInput() {
        for (KeyMapping binding : hiddenBindings) {
            binding.setDown(false);
            while (binding.consumeClick()) {
                // Discard presses queued during the pause.
            }
        }
    }

    static boolean isManagedBinding(KeyMapping binding) {
        String name = binding.getName();
        return name.startsWith("key.dash-client.")
                || name.startsWith("key.basefinder.")
                || name.equals("Toggle Nightvision");
    }
}
