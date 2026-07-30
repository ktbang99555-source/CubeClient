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
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        String text = formatLine(cpuLoad, usedMemory);
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    /** CPU는 0.0~1.0(getProcessCpuLoad 그대로) 또는 측정 전이면 음수, 메모리는 바이트. */
    public static String formatLine(double cpuLoad, long usedMemoryBytes) {
        String cpuText = cpuLoad < 0
            ? "CPU 측정 중"
            : "CPU " + Math.round(cpuLoad * 100) + "%";
        long megabytes = Math.round(usedMemoryBytes / (1024.0 * 1024.0));
        return cpuText + " | RAM " + megabytes + "MB";
    }
}
