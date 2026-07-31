package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

public class PingDisplay implements PositionedHudFeature {
    @Override
    public String id() {
        return "ping";
    }

    @Override
    public String displayName() {
        return "핑 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.31, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        // 싱글플레이는 핑 개념이 없으므로 자동으로 숨긴다(사용자 확정) — 토글 자체는 켠 채로
        // 두고, 렌더링만 조건부로 건너뛴다.
        if (client.isInSingleplayer() || client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry == null) {
            // 접속 직후 탭 목록이 아직 안 왔을 수 있는 극초반 프레임 — 에러 아님, 다음 프레임에
            // 자연히 채워진다.
            return;
        }
        String text = formatLine(entry.getLatency());
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    public static String formatLine(int latencyMillis) {
        return latencyMillis + "ms";
    }
}
