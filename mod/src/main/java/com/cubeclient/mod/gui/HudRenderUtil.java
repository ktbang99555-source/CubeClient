package com.cubeclient.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * PositionedHudFeature 구현체가 공유하는 렌더링 절차: 비율 좌표를 화면 배율 기준 픽셀로
 * 바꾸고, 행렬 스택을 push/scale/pop으로 감싸 배율을 적용한 뒤, 그 안에서 실제 콘텐츠를
 * 그린다. 콜백은 텍스트뿐 아니라 아이템 아이콘(DurabilityDisplay)도 그리므로 이름과
 * 인터페이스를 내용 중립적으로 둔다 — 시그니처 자체는 원래도 텍스트 전용이 아니었다.
 */
public final class HudRenderUtil {
    private HudRenderUtil() {}

    public static void drawScaled(DrawContext context, HudPosition pos, ScaledDrawer drawer) {
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
    public interface ScaledDrawer {
        void draw(DrawContext context, int x, int y);
    }
}
