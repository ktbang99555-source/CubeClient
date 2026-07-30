package com.cubeclient.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * PositionedHudFeature 구현체가 공유하는 렌더링 절차: 비율 좌표를 화면 배율 기준 픽셀로
 * 바꾸고, 행렬 스택을 push/scale/pop으로 감싸 배율을 적용한 뒤, 그 안에서 실제 텍스트를
 * 그린다. 이 세 단계가 FpsDisplay/SpeedDisplay/CpsDisplay/PerformanceDisplay에서 전부
 * 그대로 반복되므로, 각 기능은 텍스트 내용만 다른 이 헬퍼를 호출한다.
 */
public final class HudRenderUtil {
    private HudRenderUtil() {}

    public static void drawScaledText(DrawContext context, HudPosition pos, TextDrawer drawer) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = (int) (pos.xRatio() * client.getWindow().getScaledWidth());
        int y = (int) (pos.yRatio() * client.getWindow().getScaledHeight());
        float scale = (float) pos.scale();
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        drawer.draw(context, (int) (x / scale), (int) (y / scale));
        context.getMatrices().pop();
    }

    @FunctionalInterface
    public interface TextDrawer {
        void draw(DrawContext context, int x, int y);
    }
}
