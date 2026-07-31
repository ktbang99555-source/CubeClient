package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class ToggleSprint implements Feature {
    private final CachedConfig cachedConfig;
    private boolean sprintOn;
    private boolean sprintKeyWasDown;

    public ToggleSprint(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        if (!cachedConfig.current().isEnabled(id())) {
            // 꺼져 있으면 아무 것도 강제하지 않고, 다음에 켜졌을 때 엉뚱한 edge가 안 잡히게
            // 상태를 초기화해둔다.
            sprintOn = false;
            sprintKeyWasDown = false;
            return;
        }

        boolean isDown = client.options.sprintKey.isPressed();
        sprintOn = nextToggleState(sprintOn, isDown, sprintKeyWasDown);
        sprintKeyWasDown = isDown;

        if (sprintOn) {
            client.player.setSprinting(true);
        }
    }

    /** 누르는 순간(rising edge)에만 토글한다 — 누르고 있는 동안이나 뗄 때는 상태 유지. */
    static boolean nextToggleState(boolean current, boolean isDown, boolean wasDown) {
        if (isDown && !wasDown) {
            return !current;
        }
        return current;
    }

    @Override
    public String id() {
        return "toggle_sprint";
    }

    @Override
    public String displayName() {
        return "Toggle Sprint";
    }

    @Override
    public Category category() {
        return Category.CONTROL;
    }
}
