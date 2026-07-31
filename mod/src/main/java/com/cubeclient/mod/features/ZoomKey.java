package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import com.cubeclient.mod.zoom.ZoomFovState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.function.LongSupplier;

public class ZoomKey implements Feature {
    private static final double ZOOM_FACTOR = 8.0;
    private static final double TRANSITION_SECONDS = 0.2;
    // 프레임 드랍(창 최소화, 로딩 스파이크 등)으로 델타가 과하게 커지는 순간을 대비한 상한 —
    // 없으면 그 한 프레임에 배율이 훌쩍 뛰어버릴 수 있다.
    private static final double MAX_DELTA_SECONDS = 0.1;
    // 이제 FOV는 바닐라 옵션(30~110 클램프)을 거치지 않고 믹신으로 직접 렌더 값에 꽂히므로,
    // 0이나 음수 같은 병적인 값이 들어가지 않도록 여기서 최소한의 바닥만 지킨다.
    private static final float MIN_RENDER_FOV = 1.0f;

    private final CachedConfig cachedConfig;
    private final LongSupplier clockMillis;
    private KeyBinding zoomKey;

    private boolean zooming;
    private double progress; // 0.0 = 완전히 줌 아웃, 1.0 = 완전히 줌 인
    private long lastFrameAtMillis;
    private int originalFov;
    private double originalSensitivity;

    public ZoomKey(CachedConfig cachedConfig) {
        this(cachedConfig, System::currentTimeMillis);
        this.zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cubeclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.cubeclient"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        WorldRenderEvents.START.register(this::onRenderFrame);
    }

    ZoomKey(CachedConfig cachedConfig, LongSupplier clockMillis) {
        this.cachedConfig = cachedConfig;
        this.clockMillis = clockMillis;
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

    // 안전장치 — 렌더 프레임이 어떤 이유로든 안 돌아도(예: 월드 언로드 도중) 최소 틱마다는
    // 반드시 원래 상태로 되돌아가게 보장한다. 애니메이션 진행 자체는 건드리지 않는다 —
    // 여기서도 progress를 전진시키면 프레임 훅과 두 배로 겹쳐 속도가 들쭉날쭉해진다.
    private void onTick(MinecraftClient client) {
        if (!isSafeToZoom(client)) {
            snapToUnzoomed(client);
        }
    }

    private void onRenderFrame(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isSafeToZoom(client)) {
            snapToUnzoomed(client);
            return;
        }

        boolean keyDown = isZoomKeyPressed(client);
        long now = clockMillis.getAsLong();
        if (keyDown && !zooming) {
            // 새로 줌을 시작하는 순간에만 기준값을 다시 잡는다 — 이미 진행 중인 전환 도중엔
            // originalFov/Sensitivity를 건드리지 않아야 눌렀다 떼는 걸 반복해도 기준이 안 흔들린다.
            originalFov = client.options.getFov().getValue();
            originalSensitivity = client.options.getMouseSensitivity().getValue();
            zooming = true;
            lastFrameAtMillis = now;
        }

        if (!zooming) {
            return;
        }

        double deltaSeconds = Math.max(0.0, Math.min(MAX_DELTA_SECONDS, (now - lastFrameAtMillis) / 1000.0));
        lastFrameAtMillis = now;

        double step = deltaSeconds / TRANSITION_SECONDS;
        progress += keyDown ? step : -step;
        progress = Math.max(0.0, Math.min(1.0, progress));

        // FOV는 옵션이 아니라 믹신 경로로만 나간다 — client.options.getFov()는 읽기만 하고
        // 절대 쓰지 않는다(바닐라 클램프 30 때문에 8배 줌이 중간에 끊겼던 원인).
        double targetFov = originalFov / ZOOM_FACTOR;
        double currentFov = lerp(originalFov, targetFov, progress);
        ZoomFovState.set((float) Math.max(MIN_RENDER_FOV, currentFov));

        double targetSensitivity = originalSensitivity / ZOOM_FACTOR;
        client.options.getMouseSensitivity().setValue(lerp(originalSensitivity, targetSensitivity, progress));

        if (!keyDown && progress <= 0.0) {
            ZoomFovState.clear();
            zooming = false; // 완전히 원래 상태로 돌아왔으니 더 이상 매 프레임 옵션을 건드리지 않는다
        }
    }

    private boolean isSafeToZoom(MinecraftClient client) {
        return client.player != null
            && cachedConfig.current().isEnabled(id())
            && client.currentScreen == null;
    }

    private void snapToUnzoomed(MinecraftClient client) {
        if (!zooming) {
            return;
        }
        ZoomFovState.clear();
        client.options.getMouseSensitivity().setValue(originalSensitivity);
        zooming = false;
        progress = 0.0;
    }

    /** original에서 시작해 target을 향해 progress(0~1)만큼 선형 보간한다. */
    static double lerp(double original, double target, double progress) {
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
