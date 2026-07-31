package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

import java.util.function.LongSupplier;

public class ComboCounter implements PositionedHudFeature {
    private static final long RESET_AFTER_MILLIS = 3000;
    private static final int SWING_WINDOW_TICKS = 10; // 네트워크 왕복 감안 여유

    private final LongSupplier clockMillis;
    private int combo;
    private long lastHitAtMillis;
    private boolean attackKeyWasDown;
    private LivingEntity pendingTarget;
    private int pendingSwingTicksLeft;
    private int lastPendingTargetHurtTime;
    private int lastOwnHurtTime;

    public ComboCounter() {
        this(System::currentTimeMillis);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    ComboCounter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        if (pendingTarget != null) {
            if (pendingTarget.isRemoved()) {
                pendingTarget = null;
            } else if (pendingTarget.hurtTime > 0 && lastPendingTargetHurtTime == 0) {
                combo++;
                lastHitAtMillis = clockMillis.getAsLong();
                pendingTarget = null;
            } else {
                lastPendingTargetHurtTime = pendingTarget.hurtTime;
                if (--pendingSwingTicksLeft <= 0) {
                    pendingTarget = null; // 창 만료 — 빗나간 것으로 취급, 리셋하지 않음
                }
            }
        }

        if (client.player.hurtTime > 0 && lastOwnHurtTime == 0) {
            combo = 0; // 내가 맞으면 즉시 리셋
        }
        lastOwnHurtTime = client.player.hurtTime;

        if (combo > 0 && shouldResetForTimeout(clockMillis.getAsLong(), lastHitAtMillis)) {
            combo = 0; // 3초간 무명중 리셋
        }
    }

    static boolean shouldResetForTimeout(long now, long lastHitAtMillis) {
        return now - lastHitAtMillis >= RESET_AFTER_MILLIS;
    }

    @Override
    public String id() {
        return "combo_counter";
    }

    @Override
    public String displayName() {
        return "Combo Counter";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.36, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();

        // attackKey 엣지 감지는 프레임(보통 60Hz 이상) 단위인 render()에서 수행한다. 틱(20Hz)
        // 단위인 onTick()에서 하면 사람의 클릭 속도가 그 해상도를 쉽게 넘어서 눌림-뗌이 두 틱
        // 사이에서 통째로 끝나 상승 엣지를 놓칠 수 있다 — CpsDisplay에서 실기기 테스트로 발견해
        // 고친 것과 동일한 문제. hurtTime 갱신·10틱 창·3초 타임아웃은 서버발 틱 동기화 상태라
        // onTick()에 남겨둔다.
        boolean isDown = client.options.attackKey.isPressed();
        if (isDown && !attackKeyWasDown && client.targetedEntity instanceof LivingEntity target) {
            pendingTarget = target;
            pendingSwingTicksLeft = SWING_WINDOW_TICKS;
            lastPendingTargetHurtTime = target.hurtTime;
        }
        attackKeyWasDown = isDown;

        String text = "콤보 " + combo;
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
