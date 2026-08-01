package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityBlipClassifierTest {

    @Test
    void playerIsWhite() {
        assertEquals(EntityBlipClassifier.BlipColor.PLAYER, EntityBlipClassifier.classify(true, false));
    }

    @Test
    void monsterIsHostile() {
        assertEquals(EntityBlipClassifier.BlipColor.HOSTILE, EntityBlipClassifier.classify(false, true));
    }

    @Test
    void otherLivingEntityIsFriendly() {
        assertEquals(EntityBlipClassifier.BlipColor.FRIENDLY, EntityBlipClassifier.classify(false, false));
    }

    // 실제로는 플레이어이면서 동시에 Monster인 엔티티는 있을 수 없지만, 우선순위를 명시적으로
    // 고정해둔다(호출부 판정 순서가 바뀌어도 이 함수가 항상 플레이어를 우선하도록).
    @Test
    void playerTakesPriorityOverMonsterFlag() {
        assertEquals(EntityBlipClassifier.BlipColor.PLAYER, EntityBlipClassifier.classify(true, true));
    }
}
