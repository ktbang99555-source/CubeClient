package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class SpeedDisplay implements PositionedHudFeature {
    private double lastX;
    private double lastZ;
    private boolean hasLastPosition;
    private double currentSpeed;

    public SpeedDisplay() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    // 렌더는 틱보다 자주 호출될 수 있어 매 프레임 좌표를 다시 재면 델타가 0에 가까워져 값이
    // 튄다. 틱마다 한 번만 갱신하고 render()는 그 값을 읽기만 한다.
    private void onEndTick(MinecraftClient client) {
        if (client.player == null) {
            hasLastPosition = false;
            return;
        }
        double x = client.player.getX();
        double z = client.player.getZ();
        if (hasLastPosition) {
            // 한 틱 = 1/20초, 마인크래프트 틱 레이트 고정값.
            currentSpeed = horizontalSpeed(x - lastX, z - lastZ, 1.0 / 20.0);
        }
        lastX = x;
        lastZ = z;
        hasLastPosition = true;
    }

    /** XZ 평면 거리만 잰다 — 수직(낙하·비행) 성분은 제외. */
    public static double horizontalSpeed(double dx, double dz, double deltaSeconds) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance / deltaSeconds;
    }

    @Override
    public String id() {
        return "speed";
    }

    @Override
    public String displayName() {
        return "속도 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.06, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = String.format("%.1f m/s", currentSpeed);
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
