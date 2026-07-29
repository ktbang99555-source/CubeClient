package com.cubeclient.mod.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudPositionTest {

    @Test
    void valuesWithinRangePassThroughUnchanged() {
        HudPosition pos = HudPosition.of(0.25, 0.5, 1.5);

        assertEquals(0.25, pos.xRatio());
        assertEquals(0.5, pos.yRatio());
        assertEquals(1.5, pos.scale());
    }

    @Test
    void negativeRatiosClampToZero() {
        HudPosition pos = HudPosition.of(-0.3, -1.0, 1.0);

        assertEquals(0.0, pos.xRatio());
        assertEquals(0.0, pos.yRatio());
    }

    @Test
    void ratiosAboveOneClampToOne() {
        HudPosition pos = HudPosition.of(1.5, 2.0, 1.0);

        assertEquals(1.0, pos.xRatio());
        assertEquals(1.0, pos.yRatio());
    }

    @Test
    void scaleBelowHalfClampsToHalf() {
        HudPosition pos = HudPosition.of(0.0, 0.0, 0.1);

        assertEquals(0.5, pos.scale());
    }

    @Test
    void scaleAboveThreeClampsToThree() {
        HudPosition pos = HudPosition.of(0.0, 0.0, 10.0);

        assertEquals(3.0, pos.scale());
    }

    // Gson deserialises a record by calling its canonical constructor directly through
    // reflection, never the static of() factory — the compact constructor has to clamp on its
    // own or a hand-edited config file could smuggle an out-of-range value past of() entirely.
    // Proven by calling the canonical constructor directly, bypassing of() the way Gson does.
    @Test
    void theCanonicalConstructorClampsTooNotJustTheFactory() {
        HudPosition pos = new HudPosition(-5.0, 5.0, 100.0);

        assertEquals(0.0, pos.xRatio());
        assertEquals(1.0, pos.yRatio());
        assertEquals(3.0, pos.scale());
    }
}
