package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedDisplayTest {

    @Test
    void movingThreeUnitsInOneSecondIsThreeMetersPerSecond() {
        double speed = SpeedDisplay.horizontalSpeed(3.0, 0.0, 1.0);

        assertEquals(3.0, speed, 0.0001);
    }

    // 수평만 잰다 — 낙하·비행 중 수직 성분이 값에 섞이면 안 된다.
    @Test
    void diagonalMovementUsesPythagoreanDistanceOnXZOnly() {
        double speed = SpeedDisplay.horizontalSpeed(3.0, 4.0, 1.0);

        assertEquals(5.0, speed, 0.0001);
    }

    @Test
    void halfSecondTickIntervalDoublesTheRate() {
        double speed = SpeedDisplay.horizontalSpeed(1.0, 0.0, 0.5);

        assertEquals(2.0, speed, 0.0001);
    }

    @Test
    void noMovementIsZero() {
        double speed = SpeedDisplay.horizontalSpeed(0.0, 0.0, 1.0);

        assertEquals(0.0, speed, 0.0001);
    }
}
