package com.dash.client.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class XRay {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static boolean enabled;
    // Chunk builders read this flag off the client thread.
    private static volatile boolean rendering;

    private XRay() {}

    public static boolean isEnabled() {
        return enabled;
    }

    static void toggle() {
        enabled = !enabled;
        tick(Minecraft.getInstance());
    }

    static void tick(Minecraft client) {
        boolean active = enabled && client.level != null && !SaveMode.isPaused();
        if (rendering != active) {
            rendering = active;
            if (client.level != null) {
                client.levelRenderer.allChanged();
            }
        }
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static BlockState terrainState(BlockState state) {
        return state.isAir() || OreHighlight.isOre(state) ? state : AIR;
    }
}
