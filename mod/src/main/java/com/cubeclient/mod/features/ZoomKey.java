package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ZoomKey implements Feature {
    private static final double ZOOM_FACTOR = 4.0;

    private final CachedConfig cachedConfig;
    private final KeyBinding zoomKey;
    private boolean zoomed;
    private int originalFov;
    private double originalSensitivity;

    public ZoomKey(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
        this.zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cubeclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.cubeclient"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            restoreIfZoomed(client);
            return;
        }

        boolean enabled = cachedConfig.current().isEnabled(id());
        boolean screenOpen = client.currentScreen != null;
        boolean shouldZoom = enabled && !screenOpen && zoomKey.isPressed();

        if (shouldZoom && !zoomed) {
            SimpleOption<Integer> fov = client.options.getFov();
            SimpleOption<Double> sensitivity = client.options.getMouseSensitivity();
            originalFov = fov.getValue();
            originalSensitivity = sensitivity.getValue();
            fov.setValue((int) (originalFov / ZOOM_FACTOR));
            sensitivity.setValue(originalSensitivity / ZOOM_FACTOR);
            zoomed = true;
        } else if (!shouldZoom) {
            restoreIfZoomed(client);
        }
    }

    private void restoreIfZoomed(MinecraftClient client) {
        if (!zoomed) {
            return;
        }
        client.options.getFov().setValue(originalFov);
        client.options.getMouseSensitivity().setValue(originalSensitivity);
        zoomed = false;
    }

    @Override
    public String id() {
        return "zoom";
    }

    @Override
    public String displayName() {
        return "Zoom (C키)";
    }

    @Override
    public Category category() {
        return Category.CONTROL;
    }
}
