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
    private static final int TRANSITION_TICKS = 4;

    private final CachedConfig cachedConfig;
    private final KeyBinding zoomKey;
    private double progress; // 0.0 = fully unzoomed, 1.0 = fully zoomed in
    private boolean zooming;
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
            snapToUnzoomed(client);
            return;
        }

        boolean enabled = cachedConfig.current().isEnabled(id());
        boolean screenOpen = client.currentScreen != null;
        if (!enabled || screenOpen) {
            snapToUnzoomed(client);
            return;
        }

        boolean keyDown = isZoomKeyPressed(client);
        if (keyDown && !zooming) {
            // 새로 줌을 시작하는 순간에만 기준값을 다시 잡는다 — 이미 진행 중인 전환 도중엔
            // originalFov/Sensitivity를 건드리지 않아야 눌렀다 떼는 걸 반복해도 기준이 안 흔들린다.
            originalFov = client.options.getFov().getValue();
            originalSensitivity = client.options.getMouseSensitivity().getValue();
            zooming = true;
        }

        if (!zooming) {
            return;
        }

        double step = 1.0 / TRANSITION_TICKS;
        progress += keyDown ? step : -step;
        progress = Math.max(0.0, Math.min(1.0, progress));

        client.options.getFov().setValue((int) Math.round(lerp(originalFov, ZOOM_FACTOR, progress)));
        client.options.getMouseSensitivity().setValue(lerp(originalSensitivity, ZOOM_FACTOR, progress));

        if (!keyDown && progress <= 0.0) {
            zooming = false; // 완전히 원래 상태로 돌아왔으니 더 이상 매 틱 옵션을 건드리지 않는다
        }
    }

    private void snapToUnzoomed(MinecraftClient client) {
        if (!zooming) {
            return;
        }
        client.options.getFov().setValue(originalFov);
        client.options.getMouseSensitivity().setValue(originalSensitivity);
        zooming = false;
        progress = 0.0;
    }

    /** original에서 시작해 original/factor를 향해 progress(0~1)만큼 선형 보간한다. */
    static double lerp(double original, double factor, double progress) {
        double target = original / factor;
        return original - (original - target) * progress;
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
