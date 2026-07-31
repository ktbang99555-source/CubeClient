package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleSprintTest {

    @Test
    void risingEdgeTurnsOnFromOff() {
        assertEquals(true, ToggleSprint.nextToggleState(false, true, false));
    }

    @Test
    void risingEdgeTurnsOffFromOn() {
        assertEquals(false, ToggleSprint.nextToggleState(true, true, false));
    }

    // 키를 누른 채로 여러 틱이 지나가도(눌림 유지, edge 아님) 상태가 그대로여야 한다 —
    // 안 그러면 누르고 있는 동안 매 틱 토글되어 사실상 즉시 꺼진다.
    @Test
    void heldDownWithoutEdgeDoesNotToggle() {
        assertEquals(false, ToggleSprint.nextToggleState(false, true, true));
    }

    @Test
    void notPressedDoesNotChangeState() {
        assertEquals(true, ToggleSprint.nextToggleState(true, false, false));
    }

    // 뗄 때(하강 edge)는 토글하지 않는다 — 토글은 누르는 순간에만 일어난다.
    @Test
    void releaseEdgeDoesNotToggle() {
        assertEquals(true, ToggleSprint.nextToggleState(true, false, true));
    }
}
