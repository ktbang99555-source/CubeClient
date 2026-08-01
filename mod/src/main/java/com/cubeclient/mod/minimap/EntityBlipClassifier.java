package com.cubeclient.mod.minimap;

/** 미니맵 위 엔티티 점 색상 분류. 실제 Entity 타입 판정은 호출부(TerrainMinimap)가 하고,
 * 여기엔 그 결과만 넘어온다 — Minecraft 클래스 의존 없이 순수하게 테스트하기 위함. */
public final class EntityBlipClassifier {
    private EntityBlipClassifier() {}

    public enum BlipColor {
        HOSTILE,
        FRIENDLY,
        PLAYER
    }

    public static BlipColor classify(boolean isPlayer, boolean isMonster) {
        if (isPlayer) {
            return BlipColor.PLAYER;
        }
        if (isMonster) {
            return BlipColor.HOSTILE;
        }
        return BlipColor.FRIENDLY;
    }
}
