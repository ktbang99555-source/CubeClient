package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingDisplayTest {

    @Test
    void formatsLatencyWithMsSuffix() {
        assertEquals("42ms", PingDisplay.formatLine(42));
    }

    @Test
    void zeroLatencyIsStillShown() {
        assertEquals("0ms", PingDisplay.formatLine(0));
    }
}
