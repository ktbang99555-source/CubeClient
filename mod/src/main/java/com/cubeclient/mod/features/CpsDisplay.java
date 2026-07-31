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
    private boolean attackKeyWasDown;

    public CpsDisplay() {
        this(System::currentTimeMillis);
        // wasPressed()는 큐를 소모하는 방식인데, 마인크래프트 자체도 매 틱 공격/채굴 처리를
        // 위해 이 같은 attackKey의 wasPressed()를 먼저 소모한다 — 그러면 우리 쪽 while 루프엔
        // 아무것도 안 남아 CPS가 항상 0으로 보인다(실기기 테스트로 발견). isPressed()는 소모되지
        // 않는 "지금 눌려있나" 상태만 보므로, 이전 틱과 비교해 눌림 시작(edge)만 직접 잡는다.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isDown = client.options.attackKey.isPressed();
            if (isDown && !attackKeyWasDown) {
                recordClick();
            }
            attackKeyWasDown = isDown;
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
