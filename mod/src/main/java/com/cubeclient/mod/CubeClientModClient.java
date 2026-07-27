package com.cubeclient.mod;

import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.features.FpsDisplay;
import com.cubeclient.mod.gui.ClientSettingsButton;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

public class CubeClientModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path fallback = FabricLoader.getInstance().getConfigDir().resolve("cubeclient");
        Path configFile = ConfigStore.resolveConfigDir(fallback).resolve("mod-config.json");
        ConfigStore configStore = new ConfigStore(configFile);

        FeatureRegistry registry = new FeatureRegistry();
        FpsDisplay fpsDisplay = new FpsDisplay();
        registry.register(fpsDisplay);

        ClientSettingsButton.register(registry, configStore);

        // Reads the config from disk on every rendered frame — wasteful, but deliberate: B0 is
        // about proving correctness (the screen's toggle and the HUD's on/off state can never
        // disagree), not performance. A cached-then-invalidated version is a reasonable follow-up
        // once there's more than one HUD feature reading it — B1's HUD framework task is the
        // natural place to introduce a shared in-memory config cache all features read from,
        // instead of each one hitting disk independently the way this task does.
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            boolean enabled;
            try {
                enabled = configStore.load().isEnabled(fpsDisplay.id());
            } catch (IOException e) {
                enabled = false;
            }
            if (enabled) {
                fpsDisplay.render(context);
            }
        });
    }
}
