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

    private boolean isZoomKeyPressed(MinecraftClient client) {
        InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(zoomKey);
        if (boundKey.getCategory() != InputUtil.Type.KEYSYM) {
            // 마우스 버튼 등으로 재바인딩된 경우엔 glfwGetKey로 확인할 수 없다 — 이 경로는
            // KeyBinding 자신의 isPressed()로 폴백한다. 기본값 C(키보드)가 겪는 충돌만 확실히
            // 피하면 되는 게 목적이라, 재바인딩까지 완벽히 커버하는 건 범위 밖으로 둔다.
            return zoomKey.isPressed();
        }
        return InputUtil.isKeyPressed(client.getWindow().getHandle(), boundKey.getCode());
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            restoreIfZoomed(client);
            return;
        }

        boolean enabled = cachedConfig.current().isEnabled(id());
        boolean screenOpen = client.currentScreen != null;
        boolean shouldZoom = enabled && !screenOpen && isZoomKeyPressed(client);

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
