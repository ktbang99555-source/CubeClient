package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpsDisplayTest {

    @Test
    void noClicksIsZeroCps() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        assertEquals(0, display.currentCps());
    }

    @Test
    void threeClicksWithinOneSecondCountAsThree() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        now[0] = 300;
        display.recordClick();
        now[0] = 600;
        display.recordClick();
        now[0] = 900;

        assertEquals(3, display.currentCps());
    }

    // 1초 롤링 윈도우 — 1초보다 오래된 클릭은 더 이상 세지 않는다.
    @Test
    void clicksOlderThanOneSecondAgeOutOfTheWindow() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        display.recordClick();
        now[0] = 1500;
        display.recordClick();

        assertEquals(1, display.currentCps());
    }

    @Test
    void clickAtExactlyOneSecondAgoIsExcluded() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        now[0] = 1000;

        assertEquals(0, display.currentCps());
    }
}
