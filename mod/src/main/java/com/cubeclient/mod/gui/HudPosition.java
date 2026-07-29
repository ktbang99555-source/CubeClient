package com.cubeclient.mod.gui;

/**
 * 화면 비율(0.0~1.0) + 배율로 저장하는 HUD 요소 위치. 절대 픽셀이 아니라 비율인 이유는 창
 * 크기·GUI 배율이 바뀌어도 상대 위치가 유지되게 하기 위함. 컴팩트 생성자에서 클램프하는
 * 이유는 HudPositionTest.theCanonicalConstructorClampsTooNotJustTheFactory 참고.
 */
public record HudPosition(double xRatio, double yRatio, double scale) {
    public HudPosition {
        xRatio = clamp(xRatio, 0.0, 1.0);
        yRatio = clamp(yRatio, 0.0, 1.0);
        scale = clamp(scale, 0.5, 3.0);
    }

    public static HudPosition of(double xRatio, double yRatio, double scale) {
        return new HudPosition(xRatio, yRatio, scale);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
