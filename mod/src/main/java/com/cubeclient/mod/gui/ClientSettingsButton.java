package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Adds a "클라이언트 설정" button to the title screen and the pause menu, alongside (not
 * replacing) vanilla's own "설정" — the two are intentionally separate, matching the pattern in
 * the Feather Client reference the design was built from.
 *
 * <p>Appended as its own row below the existing buttons rather than positioned beside a specific
 * vanilla button, because vanilla button widths and positions have shifted across Minecraft
 * versions before and a fixed offset calculated against one version's layout is fragile. An
 * appended row survives that.
 *
 * <p>{@link Screen#addDrawableChild} is {@code protected}, so a helper class living outside
 * {@code Screen} itself — this one — cannot call it on someone else's screen. Fabric API's
 * {@link Screens#getButtons(Screen)} exposes the same backing drawable/selectable/child lists for
 * exactly this situation; adding to that list is equivalent to {@code addDrawableChild} without
 * needing access to the protected method.
 */
public final class ClientSettingsButton {
    private ClientSettingsButton() {}

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int BOTTOM_MARGIN = 8;

    public static void register(FeatureRegistry registry, CachedConfig cachedConfig,
                                 DeathLocationStore deathLocationStore) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                addButton(
                    screen,
                    scaledWidth / 2 - BUTTON_WIDTH / 2,
                    rowBelowExistingButtons(screen, scaledHeight),
                    registry, cachedConfig, deathLocationStore);
            }
        });
    }

    /**
     * The y for a new row under whatever vanilla already drew.
     *
     * <p>The pause menu's own buttons were previously cleared by a fixed {@code height / 4 + 100}
     * offset, which landed directly on top of vanilla's bottom button — two labels rendered into
     * one box, unreadable. Vanilla's layouts move between Minecraft versions, so measuring what is
     * actually on the screen is the only offset that keeps working.
     *
     * <p>Falls back to sitting just above the bottom edge when there is no room left below the
     * existing rows, which is better than being drawn off-screen entirely.
     */
    private static int rowBelowExistingButtons(Screen screen, int scaledHeight) {
        int lowestBottom = 0;
        for (ClickableWidget widget : Screens.getButtons(screen)) {
            lowestBottom = Math.max(lowestBottom, widget.getY() + widget.getHeight());
        }

        int proposed = lowestBottom + ROW_GAP;
        int highestAllowed = scaledHeight - BUTTON_HEIGHT - BOTTOM_MARGIN;
        return Math.min(proposed, highestAllowed);
    }

    private static void addButton(Screen screen, int x, int y,
                                   FeatureRegistry registry, CachedConfig cachedConfig,
                                   DeathLocationStore deathLocationStore) {
        ButtonWidget button = ButtonWidget.builder(Text.literal("클라이언트 설정"), b -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new ModListScreen(screen, registry, cachedConfig, deathLocationStore));
        }).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        Screens.getButtons(screen).add(button);
    }
}
