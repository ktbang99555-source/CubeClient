package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.death.DeathLocation;
import com.cubeclient.mod.death.DeathLocationFilter;
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.death.WorldIdentity;
import com.cubeclient.mod.features.DeathLocationDisplay;
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
import java.util.ArrayList;
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
    private final DeathLocationStore deathLocationStore;
    private final KeyBinding minimapKey;
    private NativeImageBackedTexture texture;
    private boolean minimapKeyWasDown;
    private List<Dot> cachedDots = List.of();

    public TerrainMinimap(CachedConfig cachedConfig, DeathLocationStore deathLocationStore) {
        this.cachedConfig = cachedConfig;
        this.deathLocationStore = deathLocationStore;
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
        // client.currentScreen == null 가드: 바닐라가 화면 열린 동안 KeyBinding 눌림을 안
        // 갱신해서 지금은 이 가드가 없어도 사실상 안전하지만(최종 리뷰에서 바이트코드로 확인),
        // ZoomKey.isSafeToZoom과 같은 이유로 방어적으로 넣어둔다 — 나중에 그 동작이 바뀌면
        // ModListScreen이 자기 로컬 config 스냅샷을 저장할 때 이 토글을 조용히 덮어쓸 수 있다.
        if (isDown && !minimapKeyWasDown && client.currentScreen == null) {
            toggleEnabled();
        }
        minimapKeyWasDown = isDown;

        if (client.player == null || client.world == null || !cachedConfig.current().isEnabled(id())) {
            return;
        }

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();

        Set<ChunkCoord> needed = new HashSet<>(MinimapMath.chunksInRadius(playerX, playerZ, RADIUS_BLOCKS));
        chunkCache.tick(client.world, client.world.getRegistryKey(), needed);

        // 지형 합성(16,384픽셀 루프)과 텍스처 업로드, 엔티티 점 계산은 매 프레임(초당
        // 60~200+회)이 아니라 여기, 고정 20Hz 틱에서만 한다 — "매 프레임/매 틱 무거운 작업
        // 금지" 원칙(최종 리뷰에서 엔티티 점 조회가 여전히 render()에 남아있던 걸 발견).
        // render()는 여기서 만든 텍스처와 점 목록을 그대로 그리기만 한다.
        if (texture == null) {
            texture = new NativeImageBackedTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
        }

        int[] pixels = MinimapCompositor.composite(TEXTURE_SIZE, RADIUS_BLOCKS, playerX, playerZ, chunkCache);
        stampArrow(pixels, client.player.getYaw());

        NativeImage image = texture.getImage();
        for (int py = 0; py < TEXTURE_SIZE; py++) {
            for (int px = 0; px < TEXTURE_SIZE; px++) {
                image.setColorArgb(px, py, pixels[py * TEXTURE_SIZE + px]);
            }
        }
        texture.upload();

        // 지형과 같은 스냅된 기준점을 써야 엔티티 점이 지형과 같은 시점에 갱신된다 — 따로
        // playerX/playerZ를 그대로 쓰면 지형은 픽셀 단위로만 갱신되는데 점은 매끄럽게 움직여서
        // 서로 어긋나 보인다(최종 리뷰에서 발견).
        double snappedPlayerX = MinimapCompositor.snapToPixelGrid(playerX, TEXTURE_SIZE, RADIUS_BLOCKS);
        double snappedPlayerZ = MinimapCompositor.snapToPixelGrid(playerZ, TEXTURE_SIZE, RADIUS_BLOCKS);
        cachedDots = computeDots(client, snappedPlayerX, snappedPlayerZ);
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

        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) -> {
            ctx.drawTexture(RenderLayer::getGuiTextured, TEXTURE_ID,
                x, y, 0f, 0f, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            for (Dot dot : cachedDots) {
                ctx.fill(x + dot.px() - 1, y + dot.py() - 1, x + dot.px() + 1, y + dot.py() + 1, dot.argb());
            }
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

    // 화면 픽셀 오프셋(px,py, 텍스처 로컬 좌표계)과 색을 미리 계산해둔 스냅샷 — render()는
    // 이걸 그리기만 하고 엔티티 조회는 하지 않는다.
    private record Dot(int px, int py, int argb) {}

    private List<Dot> computeDots(MinecraftClient client, double snappedPlayerX, double snappedPlayerZ) {
        double playerY = client.player.getY();
        Box searchBox = new Box(
            snappedPlayerX - RADIUS_BLOCKS, playerY - 64, snappedPlayerZ - RADIUS_BLOCKS,
            snappedPlayerX + RADIUS_BLOCKS, playerY + 64, snappedPlayerZ + RADIUS_BLOCKS);
        List<Entity> nearby = client.world.getOtherEntities(client.player, searchBox,
            entity -> entity instanceof LivingEntity);

        double half = TEXTURE_SIZE / 2.0;
        double blocksPerPixel = RADIUS_BLOCKS / half;
        List<Dot> dots = new ArrayList<>();
        for (Entity entity : nearby) {
            double dx = entity.getX() - snappedPlayerX;
            double dz = entity.getZ() - snappedPlayerZ;
            if (!MinimapMath.isColumnWithinRadius(dx, dz, RADIUS_BLOCKS)) {
                continue;
            }
            EntityBlipClassifier.BlipColor blip = EntityBlipClassifier.classify(
                entity instanceof PlayerEntity, entity instanceof Monster);
            int px = (int) (dx / blocksPerPixel + half);
            int py = (int) (dz / blocksPerPixel + half);
            dots.add(new Dot(px, py, blipArgb(blip)));
        }
        addDeathDots(dots, client, snappedPlayerX, snappedPlayerZ);
        return dots;
    }

    private static final int DEATH_MARKER_ARGB = 0xFF9B59B6;

    private void addDeathDots(List<Dot> dots, MinecraftClient client, double snappedPlayerX, double snappedPlayerZ) {
        // 미니맵 자신의 켜짐 여부는 이미 onTick 초반에 확인했지만, 죽은위치 기능은 별도
        // 토글이다 — 데이터는 DeathLocationStore에 계속 남아있으므로(그게 B5의 핵심,
        // 사용자가 "전체 삭제"하기 전까진 유지) 이 가드가 없으면 기능을 꺼도 미니맵 점이
        // 계속 보인다(최종 리뷰에서 발견). 설계 스펙: 두 토글 다 걸려야 함.
        if (!cachedConfig.current().isEnabled(DeathLocationDisplay.FEATURE_ID)) {
            return;
        }
        String worldId = WorldIdentity.currentWorldId(client);
        String dimensionId = WorldIdentity.currentDimensionId(client.world);
        List<DeathLocation> visible =
            DeathLocationFilter.forCurrentWorld(deathLocationStore.getAll(), worldId, dimensionId);

        double half = TEXTURE_SIZE / 2.0;
        double blocksPerPixel = RADIUS_BLOCKS / half;
        for (DeathLocation location : visible) {
            double dx = location.x() - snappedPlayerX;
            double dz = location.z() - snappedPlayerZ;
            if (!MinimapMath.isColumnWithinRadius(dx, dz, RADIUS_BLOCKS)) {
                continue;
            }
            int px = (int) (dx / blocksPerPixel + half);
            int py = (int) (dz / blocksPerPixel + half);
            dots.add(new Dot(px, py, DEATH_MARKER_ARGB));
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
        return "미니맵 (M키)";
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
