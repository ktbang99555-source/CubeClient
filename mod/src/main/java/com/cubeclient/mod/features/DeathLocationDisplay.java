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
    public static final String FEATURE_ID = "death_location";

    // 실기기에서 눈으로 보고 조정할 값들 — BeaconBlockEntityRenderer.renderBeam의 정확한 단위가
    // 시그니처만으론 확정 안 됨(위 "확인 안 된 채 남겨두는 것" 참고).
    // VertexConsumer.color(int)는 ARGB로 해석하므로 alpha 바이트를 반드시 채워야 한다 —
    // 0xFF0000은 alpha=0x00이라 완전 투명(최종 리뷰에서 발견).
    private static final int BEAM_COLOR = 0xFFFF0000;
    private static final float BEAM_WIDTH_SCALE = 0.4f;
    private static final float BEAM_GLOW_SCALE = 0.25f;

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
        if (client.player == null || client.world == null) {
            // 월드/서버를 나간 동안 lastHealth를 그대로 두면, 다음 월드에 낮은 체력으로
            // 재접속했을 때 20 -> 낮은값으로 오인되어 새 월드 로그인 좌표에 가짜 죽음이
            // 기록될 수 있다(최종 리뷰에서 발견). 안전값으로 리셋.
            lastHealth = 1f;
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
            // renderBeam은 내부적으로 블록 모서리 원점 기준 matrices.translate(0.5, 0.0, 0.5)를
            // 추가로 한다(바닐라 비콘 블록엔티티가 정수 블록 좌표에서 호출되기 때문). 죽은 위치의
            // 소수점 포함 엔티티 좌표를 그대로 넘기면 최종적으로 (x+0.5, z+0.5)만큼 어긋나므로
            // x/z만 블록 좌표로 내림 처리한다 — y는 어긋남과 무관하므로 그대로 둔다.
            matrices.translate(
                Math.floor(location.x()) - cameraPos.x,
                location.y() - cameraPos.y,
                Math.floor(location.z()) - cameraPos.z);
            BeaconBlockEntityRenderer.renderBeam(
                matrices, consumers, BeaconBlockEntityRenderer.BEAM_TEXTURE,
                tickDelta, 1.0f, worldTime, 0, BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT, BEAM_COLOR,
                BEAM_WIDTH_SCALE, BEAM_GLOW_SCALE);
            matrices.pop();
        }
    }

    @Override
    public String id() {
        return FEATURE_ID;
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
