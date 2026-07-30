package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.lang.management.ManagementFactory;

public class PerformanceDisplay implements PositionedHudFeature {
    private final OperatingSystemMXBean osBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

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
        double cpuLoad = osBean.getProcessCpuLoad();
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        String text = formatLine(cpuLoad, usedMemory, runtime.maxMemory());
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
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
