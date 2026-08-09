package com.cubeclient.mod.death;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathDetectorTest {

    @Test
    void fallingBelowZeroIsADeathEdge() {
        assertTrue(DeathDetector.isDeathEdge(2.0f, 0.0f));
    }

    @Test
    void alreadyDeadLastTickIsNotANewEdge() {
        assertFalse(DeathDetector.isDeathEdge(0.0f, 0.0f));
    }

    @Test
    void stayingAliveIsNotADeathEdge() {
        assertFalse(DeathDetector.isDeathEdge(5.0f, 3.0f));
    }

    @Test
    void negativeHealthCountsAsDead() {
        assertTrue(DeathDetector.isDeathEdge(1.0f, -1.0f));
    }
}
