package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
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
    }

    CpsDisplay(LongSupplier clockMillis) {
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

    // recordClick()을 직접 호출하는 호출부(테스트 등)가 곧바로 currentCps()를 부르지 않을
    // 수도 있으므로, render()/currentCps()를 거치지 않고도 오래된 타임스탬프가 정리되도록
    // recordClick()에서도 이 로직을 호출한다. 그렇지 않으면 클릭 기록이 무한정 쌓일 수 있다.
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

        // isPressed()는 소모되지 않는 상태 읽기라 매 프레임 확인해도 안전하다. 이전엔 틱(20Hz)
        // 단위로 확인했는데, 사람의 클릭 속도는 그 해상도를 쉽게 넘어서 실측 결과 CPS가
        // 실제보다 낮게 나왔다 — 프레임(보통 60Hz 이상) 단위로 확인해 그 문제를 줄인다.
        // wasPressed()(큐 소모형)를 다시 쓰지 않는 이유: 마인크래프트 자체도 매 틱 공격/채굴
        // 처리를 위해 이 같은 attackKey의 wasPressed()를 먼저 소모하므로, 우리 쪽에서 호출하는
        // 시점엔 이미 비어 있어 CPS가 항상 0으로 보인다(실기기 테스트로 발견).
        boolean isDown = client.options.attackKey.isPressed();
        if (isDown && !attackKeyWasDown) {
            recordClick();
        }
        attackKeyWasDown = isDown;

        String text = currentCps() + " CPS";
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
