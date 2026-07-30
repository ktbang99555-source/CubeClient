package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceDisplayTest {

    @Test
    void formatsCpuAndMemoryAsPercentOfMaxHeap() {
        String line = PerformanceDisplay.formatLine(0.42, 128L * 1024 * 1024, 256L * 1024 * 1024);

        assertEquals("CPU 42% | RAM 50%", line);
    }

    // getProcessCpuLoad()는 문서화된 대로 측정이 아직 준비되지 않은 처음 몇 틱 동안 음수를
    // 돌려줄 수 있다 — 크래시 대신 "측정 중"으로 보여야 한다.
    @Test
    void negativeCpuLoadIsShownAsMeasuring() {
        String line = PerformanceDisplay.formatLine(-1.0, 50L * 1024 * 1024, 100L * 1024 * 1024);

        assertEquals("CPU 측정 중 | RAM 50%", line);
    }

    @Test
    void roundsToNearestPercent() {
        String line = PerformanceDisplay.formatLine(0.336, 34L, 100L);

        assertEquals("CPU 34% | RAM 34%", line);
    }

    // Runtime.maxMemory()는 -Xmx가 설정되지 않으면 Long.MAX_VALUE("무제한")를 돌려준다.
    // 이걸 그대로 분모로 쓰면 항상 0%로 보이는 착시가 생기므로, 그 경우엔 사용량 자체를
    // 분모로 써서 100%로 보여준다 — "무제한이니 지금 쓰는 만큼이 상한"이라는 의미.
    @Test
    void unboundedMaxMemoryFallsBackToShowingFullUsage() {
        String line = PerformanceDisplay.formatLine(0.1, 500L, Long.MAX_VALUE);

        assertEquals("CPU 10% | RAM 100%", line);
    }
}
