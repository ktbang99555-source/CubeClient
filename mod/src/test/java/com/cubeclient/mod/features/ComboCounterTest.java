package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboCounterTest {

    @Test
    void doesNotResetBeforeThreeSecondsPass() {
        assertFalse(ComboCounter.shouldResetForTimeout(2999, 0));
    }

    @Test
    void resetsAtExactlyThreeSeconds() {
        assertTrue(ComboCounter.shouldResetForTimeout(3000, 0));
    }

    @Test
    void resetsPastThreeSeconds() {
        assertTrue(ComboCounter.shouldResetForTimeout(3001, 0));
    }
}
