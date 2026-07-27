package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The one feature this task ships, chosen because Minecraft already tracks and exposes an FPS
 * counter internally (MinecraftClient.getCurrentFps()) — this task is about proving the
 * registry-to-screen-to-render chain works end to end, not about building a new metric.
 */
public class FpsDisplay implements Feature {
    @Override
    public String id() {
        return "fps";
    }

    @Override
    public String displayName() {
        return "FPS 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getCurrentFps() + " FPS";
        context.drawTextWithShadow(client.textRenderer, text, 4, 4, Theme.TEXT);
    }
}
