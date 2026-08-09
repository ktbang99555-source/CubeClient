package com.cubeclient.mod.death;

/** 죽는 순간(하강 edge)만 잡는 순수 판정. 매 틱 반복 호출돼도 죽음 하나당 한 번만 true를 낸다 —
 * ToggleSprint의 눌림 edge 검출과 같은 패턴. */
public final class DeathDetector {
    private DeathDetector() {}

    public static boolean isDeathEdge(float previousHealth, float currentHealth) {
        return previousHealth > 0f && currentHealth <= 0f;
    }
}
