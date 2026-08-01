package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.minimap.ArrowShape;
import com.cubeclient.mod.minimap.ChunkCoord;
import com.cubeclient.mod.minimap.EntityBlipClassifier;
import com.cubeclient.mod.minimap.MinimapChunkCache;
import com.cubeclient.mod.minimap.MinimapCompositor;
import com.cubeclient.mod.minimap.MinimapMath;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TerrainMinimap implements PositionedHudFeature {
    private static final int TEXTURE_SIZE = 128;
    private static final double RADIUS_BLOCKS = 96.0;
    private static final int ARROW_BOX = 8;
    private static final int ARROW_ARGB = 0xFF2FA968;
    private static final Identifier TEXTURE_ID = Identifier.of("cubeclient", "minimap_composite");

    private final CachedConfig cachedConfig;
    private final MinimapChunkCache chunkCache = new MinimapChunkCache();
    private final KeyBinding minimapKey;
    private NativeImageBackedTexture texture;
    private boolean minimapKeyWasDown;

    public TerrainMinimap(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
        // M키는 B3의 C키와 달리 실제 실행 중인 인스턴스의 options.txt에서 확인한 결과 바닐라
        // 기본 키와 겹치지 않는다 — InputUtil.isKeyPressed 우회 없이 KeyBinding.isPressed()를
        // 그대로 써도 된다(위 "검증된 API 시그니처" 참고).
        this.minimapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cubeclient.minimap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.cubeclient"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    // 렌더 루프(중앙 디스패치)와 별개로 이 리스너는 스스로 등록한 것이라, 청크 캐시 예산 소비는
    // 켜짐 여부를 직접 확인해야 한다(ToggleSprint/ZoomKey와 같은 이유 — B3 아키텍처 참고). M키
    // 토글 자체는 켜짐 여부와 무관하게 항상 눌림을 감지해야 하므로 그 확인보다 먼저 처리한다.
    private void onTick(MinecraftClient client) {
        boolean isDown = minimapKey.isPressed();
        if (isDown && !minimapKeyWasDown) {
            toggleEnabled();
        }
        minimapKeyWasDown = isDown;

        if (client.player == null || client.world == null || !cachedConfig.current().isEnabled(id())) {
            return;
        }
        Set<ChunkCoord> needed = new HashSet<>(
            MinimapMath.chunksInRadius(client.player.getX(), client.player.getZ(), RADIUS_BLOCKS));
        chunkCache.tick(client.world, client.world.getRegistryKey(), needed);

        // 지형 합성(16,384픽셀 루프)과 텍스처 업로드는 매 프레임(초당 60~200+회)이 아니라
        // 여기, 고정 20Hz 틱에서만 한다 — "매 프레임/매 틱 무거운 작업 금지" 원칙은
        // MinimapChunkCache의 틱당 청크 1개 예산뿐 아니라 이 합성 단계에도 똑같이 적용된다.
        // render()는 여기서 만든 텍스처를 그대로 그리기만 한다.
        if (texture == null) {
            texture = new NativeImageBackedTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
        }

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        int[] pixels = MinimapCompositor.composite(TEXTURE_SIZE, RADIUS_BLOCKS, playerX, playerZ, chunkCache);
        stampArrow(pixels, client.player.getYaw());

        NativeImage image = texture.getImage();
        for (int py = 0; py < TEXTURE_SIZE; py++) {
            for (int px = 0; px < TEXTURE_SIZE; px++) {
                image.setColorArgb(px, py, pixels[py * TEXTURE_SIZE + px]);
            }
        }
        texture.upload();
    }

    // 모드 목록 화면의 체크박스(ModListScreen.onToggle)와 정확히 같은 read-modify-write
    // 패턴 — M키는 그 체크박스의 단축키일 뿐, 별도 상태를 두지 않는다.
    private void toggleEnabled() {
        ModConfig current = cachedConfig.current();
        Map<String, Boolean> enabled = new HashMap<>(current.enabled());
        enabled.put(id(), !current.isEnabled(id()));
        try {
            cachedConfig.save(new ModConfig(enabled, current.favorites(), current.positions()));
        } catch (IOException e) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("설정을 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        // texture == null 방어는 첫 틱이 아직 안 돈 순간(월드 로딩 직후 첫 프레임 등)을 대비한
        // 것 — onTick()이 텍스처를 만들고 채우기 전까지는 그릴 게 없다.
        if (client.player == null || client.world == null || texture == null) {
            return;
        }

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) -> {
            ctx.drawTexture(RenderLayer::getGuiTextured, TEXTURE_ID,
                x, y, 0f, 0f, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            drawEntityDots(ctx, x, y, client, playerX, playerZ);
        });
    }

    private void stampArrow(int[] pixels, float yawDegrees) {
        int half = TEXTURE_SIZE / 2;
        for (int dz = -ARROW_BOX; dz <= ARROW_BOX; dz++) {
            for (int dx = -ARROW_BOX; dx <= ARROW_BOX; dx++) {
                if (!ArrowShape.isInsideArrow(dx, dz, yawDegrees)) {
                    continue;
                }
                int px = half + dx;
                int pz = half + dz;
                if (px >= 0 && px < TEXTURE_SIZE && pz >= 0 && pz < TEXTURE_SIZE) {
                    pixels[pz * TEXTURE_SIZE + px] = ARROW_ARGB;
                }
            }
        }
    }

    private void drawEntityDots(DrawContext ctx, int x, int y, MinecraftClient client,
                                 double playerX, double playerZ) {
        double playerY = client.player.getY();
        Box searchBox = new Box(
            playerX - RADIUS_BLOCKS, playerY - 64, playerZ - RADIUS_BLOCKS,
            playerX + RADIUS_BLOCKS, playerY + 64, playerZ + RADIUS_BLOCKS);
        List<Entity> nearby = client.world.getOtherEntities(client.player, searchBox,
            entity -> entity instanceof LivingEntity);

        double half = TEXTURE_SIZE / 2.0;
        double blocksPerPixel = RADIUS_BLOCKS / half;
        for (Entity entity : nearby) {
            double dx = entity.getX() - playerX;
            double dz = entity.getZ() - playerZ;
            if (!MinimapMath.isColumnWithinRadius(dx, dz, RADIUS_BLOCKS)) {
                continue;
            }
            EntityBlipClassifier.BlipColor blip = EntityBlipClassifier.classify(
                entity instanceof PlayerEntity, entity instanceof Monster);
            int color = blipArgb(blip);
            int px = x + (int) (dx / blocksPerPixel + half);
            int pz = y + (int) (dz / blocksPerPixel + half);
            ctx.fill(px - 1, pz - 1, px + 1, pz + 1, color);
        }
    }

    private static int blipArgb(EntityBlipClassifier.BlipColor blip) {
        return switch (blip) {
            case HOSTILE -> 0xFFE05A5A;
            case FRIENDLY -> 0xFF6FCF7A;
            case PLAYER -> 0xFFF2F2F2;
        };
    }

    @Override
    public String id() {
        return "minimap";
    }

    @Override
    public String displayName() {
        return "미니맵";
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.72, 0.03, 0.5);
    }

    @Override
    public int renderedWidth() {
        return TEXTURE_SIZE;
    }

    @Override
    public int renderedHeight() {
        return TEXTURE_SIZE;
    }
}
