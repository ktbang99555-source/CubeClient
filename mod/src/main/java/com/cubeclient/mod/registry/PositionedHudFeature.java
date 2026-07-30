package com.cubeclient.mod.registry;

import com.cubeclient.mod.gui.HudPosition;
import net.minecraft.client.gui.DrawContext;

/**
 * 화면 위에 실제로 그려지고 위치·크기를 갖는 기능. Feature 자체에 이 메서드들을 넣지 않는
 * 이유: 조작·월드·서버 카테고리의 기능(예: Toggle Sneak/Sprint) 상당수는 화면에 좌표를 가진
 * 요소가 아니라서, 여기 넣으면 그런 기능들도 안 쓸 메서드를 강제로 구현하게 된다.
 */
public interface PositionedHudFeature extends Feature {
    HudPosition defaultPosition();
    void render(DrawContext context, HudPosition resolvedPosition);
}
