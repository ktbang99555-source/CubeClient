package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrowShapeTest {

    // yaw=0은 바닐라 기준 남쪽(+Z, 화면에서 아래쪽)을 바라본다.
    @Test
    void yawZeroPointsSouthDownScreen() {
        assertTrue(ArrowShape.isInsideArrow(0, 3, 0));
        assertFalse(ArrowShape.isInsideArrow(0, -6, 0));
    }

    // yaw=180은 북쪽(-Z, 화면 위쪽).
    @Test
    void yaw180PointsNorthUpScreen() {
        assertTrue(ArrowShape.isInsideArrow(0, -3, 180));
        assertFalse(ArrowShape.isInsideArrow(0, 6, 180));
    }

    // yaw=90은 서쪽(-X, 화면 왼쪽).
    @Test
    void yaw90PointsWest() {
        assertTrue(ArrowShape.isInsideArrow(-3, 0, 90));
        assertFalse(ArrowShape.isInsideArrow(6, 0, 90));
    }

    @Test
    void farAwayPointIsAlwaysOutside() {
        assertFalse(ArrowShape.isInsideArrow(50, 50, 0));
    }
}
