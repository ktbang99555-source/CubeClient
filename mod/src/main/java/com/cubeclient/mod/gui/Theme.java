package com.cubeclient.mod.gui;

/**
 * The launcher's Deepslate palette (ui/renderer/styles.css), translated to 0xAARRGGBB ints for
 * Minecraft's DrawContext fill/text calls. Values must match the launcher exactly — this is a
 * brand identity, not a separate design.
 */
public final class Theme {
    private Theme() {}

    public static final int GROUND = 0xFF0F1216;
    public static final int PANEL = 0xFF151A20;
    public static final int BORDER = 0xFF232932;
    public static final int TEXT = 0xFFE4E8EE;
    public static final int MUTED = 0xFF8A94A3;
    public static final int ACCENT = 0xFF2FA968;
    public static final int WARNING = 0xFFE0A23C;
}
