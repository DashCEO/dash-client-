package com.dash.client.client;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import static com.dash.client.client.DashLanguage.text;

/** Ends Dash activity for the current game process from a private singleplayer world. */
final class PauseManager {
    enum State { ACTIVE, PAUSING, PAUSED }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<State> STATE = new AtomicReference<>(State.ACTIVE);
    private static KeyMapping[] dashBindings = {};

    private PauseManager() {}

    static State state() {
        return STATE.get();
    }

    static void pause(Minecraft client) {
        if (state() != State.ACTIVE) return;
        if (!client.isSameThread()) {
            client.execute(() -> pause(client));
            return;
        }
        var server = client.getSingleplayerServer();
        var connection = client.getConnection();
        if (server == null || server.isPublished() || client.level == null || client.player == null
                || connection == null || !connection.getConnection().isMemoryConnection()) {
            message(client, text("Пауза Dash доступна в одиночному світі.",
                    "Пауза Dash доступна в одиночном мире.", "Dash Pause is available in singleplayer."));
            return;
        }
        if (!STATE.compareAndSet(State.ACTIVE, State.PAUSING)) return;

        try {
            step("input", () -> {
                dashBindings = Arrays.stream(client.options.keyMappings)
                        .filter(SaveMode::isManagedBinding).toArray(KeyMapping[]::new);
                discardBindingInput();
                DashBindInput.reset();
            });
            step("Dash menu", () -> {
                if (client.screen instanceof DashDashboardScreen || client.screen instanceof DashClientClient.DashClickGui) {
                    client.setScreen(null);
                }
            });
            step("module cleanup", () -> DashClientClient.onPause(client));
            step("terrain renderer", () -> TerrainVisuals.refresh(client));
            step("chat layout", () -> client.gui.getChat().rescaleChat());
            step("pause notice", () -> message(client, text("Модулі призупинено на цей запуск гри.",
                    "Модули приостановлены до завершения игры.", "Modules paused for this game session.")));
            LOGGER.info("Dash Pause completed in the current singleplayer world");
        } finally {
            STATE.set(State.PAUSED);
        }
    }

    static void discardBindingInput() {
        for (KeyMapping key : dashBindings) {
            key.setDown(false);
            while (key.consumeClick()) {
                // Discard input for Dash bindings; vanilla bindings use Minecraft's normal input path.
            }
        }
    }

    static void step(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Error while pausing Dash component {}", name, exception);
        }
    }

    private static void message(Minecraft client, String message) {
        client.gui.getChat().addMessage(Component.literal("[Dash] " + message));
    }
}
