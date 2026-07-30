package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceDisplayTest {

    @Test
    void formatsCpuAsPercentAndMemoryInMegabytes() {
        String line = PerformanceDisplay.formatLine(0.42, 256L * 1024 * 1024);

        assertEquals("CPU 42% | RAM 256MB", line);
    }

    // getProcessCpuLoad()는 문서화된 대로 측정이 아직 준비되지 않은 처음 몇 틱 동안 음수를
    // 돌려줄 수 있다 — 크래시 대신 "측정 중"으로 보여야 한다.
    @Test
    void negativeCpuLoadIsShownAsMeasuring() {
        String line = PerformanceDisplay.formatLine(-1.0, 100L * 1024 * 1024);

        assertEquals("CPU 측정 중 | RAM 100MB", line);
    }

    @Test
    void roundsToNearestPercentAndMegabyte() {
        String line = PerformanceDisplay.formatLine(0.336, (long) (10.6 * 1024 * 1024));

        assertEquals("CPU 34% | RAM 11MB", line);
    }
}
