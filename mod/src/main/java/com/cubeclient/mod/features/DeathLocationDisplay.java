package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.death.DeathDetector;
import com.cubeclient.mod.death.DeathLocation;
import com.cubeclient.mod.death.DeathLocationFilter;
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.death.WorldIdentity;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.util.List;

public class DeathLocationDisplay implements Feature {
    // 실기기에서 눈으로 보고 조정할 값들 — BeaconBlockEntityRenderer.renderBeam의 정확한 단위가
    // 시그니처만으론 확정 안 됨(위 "확인 안 된 채 남겨두는 것" 참고).
    private static final int BEAM_COLOR = 0xFF0000;
    private static final float BEAM_WIDTH_SCALE = 0.4f;
    private static final float BEAM_GLOW_SCALE = 0.25f;
    private static final int BEAM_MAX_HEIGHT = 320;

    private final CachedConfig cachedConfig;
    private final DeathLocationStore store;
    // 로그인 직후 첫 틱에 거짓 죽음 판정이 안 나도록 양수로 시작.
    private float lastHealth = 1f;

    public DeathLocationDisplay(CachedConfig cachedConfig, DeathLocationStore store) {
        this.cachedConfig = cachedConfig;
        this.store = store;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onRenderBeams);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        float currentHealth = client.player.getHealth();
        if (cachedConfig.current().isEnabled(id()) && DeathDetector.isDeathEdge(lastHealth, currentHealth)) {
            recordDeath(client);
        }
        lastHealth = currentHealth;
    }

    private void recordDeath(MinecraftClient client) {
        String worldId = WorldIdentity.currentWorldId(client);
        String dimensionId = WorldIdentity.currentDimensionId(client.world);
        Vec3d pos = client.player.getPos();
        try {
            store.add(new DeathLocation(worldId, dimensionId, pos.x, pos.y, pos.z));
        } catch (IOException e) {
            client.player.sendMessage(Text.literal("죽은 위치를 저장하지 못했습니다: " + e.getMessage()), false);
        }
    }

    private void onRenderBeams(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !cachedConfig.current().isEnabled(id())) {
            return;
        }

        String worldId = WorldIdentity.currentWorldId(client);
        String dimensionId = WorldIdentity.currentDimensionId(client.world);
        List<DeathLocation> visible = DeathLocationFilter.forCurrentWorld(store.getAll(), worldId, dimensionId);
        if (visible.isEmpty()) {
            return;
        }

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        float tickDelta = context.tickCounter().getTickDelta(true);
        long worldTime = client.world.getTime();

        for (DeathLocation location : visible) {
            matrices.push();
            matrices.translate(
                location.x() - cameraPos.x, location.y() - cameraPos.y, location.z() - cameraPos.z);
            BeaconBlockEntityRenderer.renderBeam(
                matrices, consumers, BeaconBlockEntityRenderer.BEAM_TEXTURE,
                tickDelta, 1.0f, worldTime, 0, BEAM_MAX_HEIGHT, BEAM_COLOR,
                BEAM_WIDTH_SCALE, BEAM_GLOW_SCALE);
            matrices.pop();
        }
    }

    @Override
    public String id() {
        return "death_location";
    }

    @Override
    public String displayName() {
        return "죽은 위치 표시";
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }
}
