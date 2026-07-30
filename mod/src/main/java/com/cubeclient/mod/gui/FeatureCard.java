package com.cubeclient.mod.gui;

import com.cubeclient.mod.registry.Feature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * One card in the mod-list grid: an icon, the name, a favorite heart, a toggle, and a gear.
 *
 * <p>The gear (per-feature settings) is drawn in the muted colour and has no click handler — B0
 * has no feature with options yet, and a visibly inert control that says "not yet" beats hiding
 * the affordance entirely, matching the launcher's own "+ 버전 추가 (준비 중)" pattern.
 *
 * <p>The icon and the gear are drawn as filled rectangles rather than font glyphs. Minecraft's
 * default font falls back for a lot of Unicode, but a symbol it cannot resolve renders as a
 * missing-glyph box, and a card whose picture is a white rectangle looks broken rather than
 * minimal. Shapes always draw.
 */
public class FeatureCard extends ClickableWidget {
    private final Feature feature;
    private boolean enabled;
    private boolean favorite;
    private final Consumer<Feature> onToggle;
    private final Consumer<Feature> onFavoriteToggle;

    public FeatureCard(int x, int y, int width, int height, Feature feature,
                        boolean enabled, boolean favorite,
                        Consumer<Feature> onToggle, Consumer<Feature> onFavoriteToggle) {
        super(x, y, width, height, Text.literal(feature.displayName()));
        this.feature = feature;
        this.enabled = enabled;
        this.favorite = favorite;
        this.onToggle = onToggle;
        this.onFavoriteToggle = onFavoriteToggle;
    }

    public Feature feature() {
        return feature;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(getX(), getY(), getX() + width, getY() + height, Theme.PANEL);
        context.drawBorder(getX(), getY(), width, height, Theme.BORDER);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer, feature.displayName(), getX() + 8, getY() + 8, Theme.TEXT, false);

        // Favorite heart, top-right corner of the card. Drawn as a shape, not a "♥"/"♡" glyph —
        // Minecraft's default font doesn't have those characters and renders a missing-glyph box
        // instead, the exact problem the icon/gear below were already redesigned to avoid.
        drawHeart(context, getX() + width - 16, getY() + 8, favorite ? Theme.ACCENT : Theme.MUTED);

        drawIcon(context, getX() + width / 2 - 12, getY() + 26);

        int toggleY = getY() + height - 20;
        drawGear(context, getX() + 8, toggleY + 3);

        // Toggle starts after the gear so the two never overlap.
        int toggleColor = enabled ? Theme.ACCENT : Theme.BORDER;
        int toggleLeft = getX() + 26;
        context.fill(toggleLeft, toggleY, getX() + width - 8, toggleY + 14, toggleColor);
        context.drawCenteredTextWithShadow(textRenderer, enabled ? "켬" : "끔",
            (toggleLeft + getX() + width - 8) / 2, toggleY + 3, enabled ? Theme.GROUND : Theme.MUTED);
    }

    /**
     * A 24x24 pictogram, currently derived from the feature's category rather than the feature
     * itself — B0 ships one feature, so per-feature artwork would be four drawings of nothing.
     * When B1 onwards adds real features, this is where a per-feature icon hook belongs.
     */
    private void drawIcon(DrawContext context, int x, int y) {
        int tint = enabled ? Theme.ACCENT : Theme.MUTED;
        switch (feature.category()) {
            case HUD -> {
                // A screen: outlined box with a readout bar inside.
                context.drawBorder(x, y, 24, 18, tint);
                context.fill(x + 4, y + 6, x + 14, y + 9, tint);
            }
            case CONTROL -> {
                // A key: cap above, stem below.
                context.fill(x + 4, y, x + 20, y + 8, tint);
                context.fill(x + 9, y + 8, x + 15, y + 18, tint);
            }
            case WORLD -> {
                // A map pin: head and point.
                context.fill(x + 8, y, x + 16, y + 10, tint);
                context.fill(x + 10, y + 10, x + 14, y + 18, tint);
            }
            case SERVER -> {
                // A rack: three stacked units.
                context.fill(x + 2, y, x + 22, y + 4, tint);
                context.fill(x + 2, y + 7, x + 22, y + 11, tint);
                context.fill(x + 2, y + 14, x + 22, y + 18, tint);
            }
        }
    }

    /** A small pixel heart — filled bumps, a body, and a tapering point. */
    private void drawHeart(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 2, y + 2, color);
        context.fill(x + 3, y, x + 5, y + 2, color);
        context.fill(x, y + 2, x + 5, y + 4, color);
        context.fill(x + 1, y + 4, x + 4, y + 5, color);
        context.fill(x + 2, y + 5, x + 3, y + 6, color);
    }

    /** Inert in B0 — drawn muted so it reads as "there, but not yet". */
    private void drawGear(DrawContext context, int x, int y) {
        context.fill(x + 3, y, x + 9, y + 12, Theme.MUTED);
        context.fill(x, y + 3, x + 12, y + 9, Theme.MUTED);
        context.fill(x + 4, y + 4, x + 8, y + 8, Theme.PANEL);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // Three regions share one card, distinguished by hit-testing rather than by nesting
        // widgets — a ClickableWidget inside another fights Minecraft's own mouse dispatch.
        boolean hitHeart = mouseX >= getX() + width - 20 && mouseY <= getY() + 20;
        if (hitHeart) {
            favorite = !favorite;
            onFavoriteToggle.accept(feature);
            return;
        }

        // The gear is drawn but does nothing in B0. Swallowing the click rather than letting it
        // fall through matters: the gear sits inside the toggle row, so without this, aiming at
        // "settings" would silently switch the feature off instead.
        boolean hitGear = mouseX >= getX() + 8 && mouseX <= getX() + 20
            && mouseY >= getY() + height - 17;
        if (hitGear) {
            return;
        }

        enabled = !enabled;
        onToggle.accept(feature);
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
