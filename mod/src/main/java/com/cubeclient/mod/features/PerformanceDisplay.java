package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import com.sun.management.OperatingSystemMXBean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.lang.management.ManagementFactory;

public class PerformanceDisplay implements PositionedHudFeature {
    // getProcessCpuLoad()는 OS 질의라 가볍지 않다 — 매 렌더 프레임(초당 60~100회 이상) 호출하면
    // 그 자체로 FPS를 눈에 띄게 깎아먹는다(실기기 테스트로 발견, ~40FPS까지 하락). CPU/RAM은
    // 어차피 초 단위로만 의미 있게 바뀌므로 틱마다가 아니라 1초에 한 번만 재고, render()는
    // 캐시된 값을 읽기만 한다.
    private static final int SAMPLE_INTERVAL_TICKS = 20;

    private final OperatingSystemMXBean osBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private int ticksSinceSample = SAMPLE_INTERVAL_TICKS;
    private double cachedCpuLoad = -1;
    private long cachedUsedMemory;
    private long cachedMaxMemory = Long.MAX_VALUE;

    public PerformanceDisplay() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticksSinceSample++;
            if (shouldSample(ticksSinceSample, SAMPLE_INTERVAL_TICKS)) {
                ticksSinceSample = 0;
                cachedCpuLoad = osBean.getProcessCpuLoad();
                Runtime runtime = Runtime.getRuntime();
                cachedUsedMemory = runtime.totalMemory() - runtime.freeMemory();
                cachedMaxMemory = runtime.maxMemory();
            }
        });
    }

    /** Pure throttle decision, split out from the tick listener so it's testable without a
     * MinecraftClient/OperatingSystemMXBean in play. */
    static boolean shouldSample(int ticksSinceLastSample, int intervalTicks) {
        return ticksSinceLastSample >= intervalTicks;
    }

    @Override
    public String id() {
        return "performance";
    }

    @Override
    public String displayName() {
        return "성능 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.16, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = formatLine(cachedCpuLoad, cachedUsedMemory, cachedMaxMemory);
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    /**
     * CPU는 0.0~1.0(getProcessCpuLoad 그대로) 또는 측정 전이면 음수, 메모리는 바이트.
     * RAM은 사용량을 최대 힙(-Xmx) 대비 비율로 보여준다 — F3 디버그 화면과 같은 기준.
     * maxMemoryBytes가 Long.MAX_VALUE면 -Xmx가 설정되지 않은 것(무제한)이라 나눗셈이
     * 의미가 없으므로, 그 경우엔 현재 할당된 힙(totalMemory 역할의 usedMemoryBytes 상한이
     * 아니라 usedMemoryBytes 자체)을 기준으로 100%로 표시해 0%로 나오는 착시를 피한다.
     */
    public static String formatLine(double cpuLoad, long usedMemoryBytes, long maxMemoryBytes) {
        String cpuText = cpuLoad < 0
            ? "CPU 측정 중"
            : "CPU " + Math.round(cpuLoad * 100) + "%";
        long percentDenominator = maxMemoryBytes == Long.MAX_VALUE ? usedMemoryBytes : maxMemoryBytes;
        long percent = percentDenominator == 0 ? 0 : Math.round(100.0 * usedMemoryBytes / percentDenominator);
        return cpuText + " | RAM " + percent + "%";
    }
}
