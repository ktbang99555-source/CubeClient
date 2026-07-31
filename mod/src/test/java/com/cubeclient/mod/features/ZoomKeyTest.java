package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoomKeyTest {

    @Test
    void progressZeroIsTheOriginalValue() {
        assertEquals(100.0, ZoomKey.lerp(100.0, 4.0, 0.0), 0.0001);
    }

    @Test
    void progressOneIsTheFullyZoomedTarget() {
        assertEquals(25.0, ZoomKey.lerp(100.0, 4.0, 1.0), 0.0001);
    }

    @Test
    void progressHalfIsHalfwayBetween() {
        assertEquals(62.5, ZoomKey.lerp(100.0, 4.0, 0.5), 0.0001);
    }
}
