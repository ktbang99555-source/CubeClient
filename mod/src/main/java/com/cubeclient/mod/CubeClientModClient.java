package com.cubeclient.mod;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.features.CpsDisplay;
import com.cubeclient.mod.features.FpsDisplay;
import com.cubeclient.mod.features.PerformanceDisplay;
import com.cubeclient.mod.features.SpeedDisplay;
import com.cubeclient.mod.gui.ClientSettingsButton;
import com.cubeclient.mod.registry.FeatureRegistry;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

public class CubeClientModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path fallback = FabricLoader.getInstance().getConfigDir().resolve("cubeclient");
        Path configFile = ConfigStore.resolveConfigDir(fallback).resolve("mod-config.json");
        CachedConfig cachedConfig = new CachedConfig(new ConfigStore(configFile));

        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new FpsDisplay());
        registry.register(new SpeedDisplay());
        registry.register(new CpsDisplay());
        registry.register(new PerformanceDisplay());

        ClientSettingsButton.register(registry, cachedConfig);

        // HudRenderCallback은 사용 중인 Fabric API 버전에서 @Deprecated로 표시되어 있다
        // (jar를 풀어 javap -v로 RuntimeVisibleAnnotations에서 확인함). 대체 API인
        // HudLayerRegistrationCallback + IdentifiedLayer로 레이어 하나만 등록하고, 그 안에서
        // 켜진 PositionedHudFeature를 전부 순회해 그린다 — 기능 토글마다 레이어를
        // 등록/해제하지 않는다.
        HudLayerRegistrationCallback.EVENT.register(drawer ->
            drawer.attachLayerAfter(IdentifiedLayer.MISC_OVERLAYS, IdentifiedLayer.of(
                Identifier.of("cubeclient", "hud"),
                (context, tickCounter) -> {
                    var config = cachedConfig.current();
                    for (var feature : registry.all()) {
                        if (feature instanceof PositionedHudFeature hudFeature
                            && config.isEnabled(hudFeature.id())) {
                            var position = config.positionOr(hudFeature.id(), hudFeature.defaultPosition());
                            hudFeature.render(context, position);
                        }
                    }
                }
            ))
        );
    }
}
