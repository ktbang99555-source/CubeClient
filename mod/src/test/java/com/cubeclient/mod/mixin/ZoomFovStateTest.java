package com.cubeclient.mod.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoomFovStateTest {

    @Test
    void startsWithNoOverride() {
        ZoomFovState.clear();
        assertTrue(ZoomFovState.get() <= 0f);
    }

    @Test
    void setStoresTheOverrideValue() {
        ZoomFovState.set(8.75f);
        assertEquals(8.75f, ZoomFovState.get(), 0.0001f);
        ZoomFovState.clear();
    }

    @Test
    void clearReturnsToNoOverride() {
        ZoomFovState.set(8.75f);
        ZoomFovState.clear();
        assertTrue(ZoomFovState.get() <= 0f);
    }
}
