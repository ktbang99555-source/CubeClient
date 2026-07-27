package com.cubeclient.mod.gui;

import com.cubeclient.mod.registry.Feature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * One card in the mod-list grid: icon placeholder, name, a toggle button, and a favorite heart.
 * The gear (per-feature settings) is drawn disabled — B0 has no feature with options yet, and a
 * disabled control that says "not yet" is better than hiding the affordance entirely, matching
 * the launcher's own "+ 버전 추가 (준비 중)" pattern.
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

        // Favorite heart, top-right corner of the card.
        context.drawText(textRenderer, favorite ? "♥" : "♡",
            getX() + width - 16, getY() + 8, favorite ? Theme.ACCENT : Theme.MUTED, false);

        int toggleColor = enabled ? Theme.ACCENT : Theme.BORDER;
        int toggleY = getY() + height - 20;
        context.fill(getX() + 8, toggleY, getX() + width - 8, toggleY + 14, toggleColor);
        context.drawCenteredTextWithShadow(textRenderer, enabled ? "켬" : "끔",
            getX() + width / 2, toggleY + 3, enabled ? Theme.GROUND : Theme.MUTED);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // The favorite heart occupies the top-right ~16px; everything else toggles the feature.
        // A dedicated hit-test rather than a second widget, since the two controls share one
        // card and a nested ClickableWidget inside another fights Minecraft's own widget/mouse
        // dispatch.
        boolean hitHeart = mouseX >= getX() + width - 20 && mouseY <= getY() + 20;
        if (hitHeart) {
            favorite = !favorite;
            onFavoriteToggle.accept(feature);
        } else {
            enabled = !enabled;
            onToggle.accept(feature);
        }
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
