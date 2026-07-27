package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

    // Starting guesses, not a verified layout — see the class-level note about Task 12's manual
    // pass. Kept as named constants specifically so they're easy to retune without hunting
    // through the lambda bodies below.
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TITLE_SCREEN_BOTTOM_MARGIN = 28;
    private static final int PAUSE_SCREEN_X_OFFSET = -(BUTTON_WIDTH / 2);
    private static final int PAUSE_SCREEN_Y_OFFSET = 100;

    public static void register(FeatureRegistry registry, ConfigStore configStore) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                addButton(
                    screen,
                    scaledWidth / 2 - BUTTON_WIDTH / 2,
                    scaledHeight - TITLE_SCREEN_BOTTOM_MARGIN,
                    registry, configStore);
            } else if (screen instanceof GameMenuScreen) {
                addButton(
                    screen,
                    scaledWidth / 2 + PAUSE_SCREEN_X_OFFSET,
                    scaledHeight / 4 + PAUSE_SCREEN_Y_OFFSET,
                    registry, configStore);
            }
        });
    }

    private static void addButton(Screen screen, int x, int y,
                                   FeatureRegistry registry, ConfigStore configStore) {
        ButtonWidget button = ButtonWidget.builder(Text.literal("클라이언트 설정"), b -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new ModListScreen(screen, registry, configStore));
        }).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        Screens.getButtons(screen).add(button);
    }
}
