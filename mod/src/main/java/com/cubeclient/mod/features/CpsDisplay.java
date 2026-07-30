package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

public class CpsDisplay implements PositionedHudFeature {
    private static final long WINDOW_MILLIS = 1000;

    private final LongSupplier clockMillis;
    private final Deque<Long> clickTimestamps = new ArrayDeque<>();

    public CpsDisplay() {
        this(System::currentTimeMillis);
        // KeyBinding.wasPressed()는 호출할 때마다 큐를 1개씩 소모한다. 틱마다 한 번만
        // 호출하면 한 틱(1/20초)에 여러 번 눌린 빠른 연타 중 한 번만 세게 되므로, while로
        // 큐를 완전히 비우면서 소모마다 클릭을 하나씩 기록한다.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (client.options.attackKey.wasPressed()) {
                recordClick();
            }
        });
    }

    public CpsDisplay(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public void recordClick() {
        clickTimestamps.addLast(clockMillis.getAsLong());
        evictExpired();
    }

    public int currentCps() {
        evictExpired();
        return clickTimestamps.size();
    }

    // CPS HUD가 꺼져 있어도 recordClick()은 매 틱 호출되므로, render()/currentCps()를
    // 거치지 않고도 오래된 타임스탬프가 계속 정리되도록 recordClick()에서도 이 로직을
    // 호출한다. 그렇지 않으면 클릭 기록이 세션 내내 무한정 쌓인다.
    private void evictExpired() {
        long now = clockMillis.getAsLong();
        while (!clickTimestamps.isEmpty() && now - clickTimestamps.peekFirst() >= WINDOW_MILLIS) {
            clickTimestamps.pollFirst();
        }
    }

    @Override
    public String id() {
        return "cps";
    }

    @Override
    public String displayName() {
        return "CPS 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.11, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = currentCps() + " CPS";
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
