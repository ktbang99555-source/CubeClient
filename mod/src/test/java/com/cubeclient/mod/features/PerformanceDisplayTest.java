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

    // getProcessCpuLoad()/Runtime 메모리 조회는 매 렌더 프레임 부르면 그 자체로 FPS를 깎아먹으므로
    // (실기기 테스트로 발견) 1초(20틱)에 한 번만 재샘플링한다. shouldSample()은 그 스로틀 판단만
    // 떼어낸 순수 로직 — MinecraftClient/OperatingSystemMXBean 없이 경계값을 직접 검증한다.
    @Test
    void shouldSampleDoesNotFireBeforeInterval() {
        assertEquals(false, PerformanceDisplay.shouldSample(19, 20));
    }

    @Test
    void shouldSampleFiresAtExactlyTheInterval() {
        assertEquals(true, PerformanceDisplay.shouldSample(20, 20));
    }

    @Test
    void shouldSampleFiresPastTheInterval() {
        assertEquals(true, PerformanceDisplay.shouldSample(25, 20));
    }
}
