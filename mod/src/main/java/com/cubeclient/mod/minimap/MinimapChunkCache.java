package com.cubeclient.mod.minimap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** 청크별 색상 배열을 캐시하고, 매 tick() 호출마다 최대 1개 청크만 새로 샘플링한다(매 프레임
 * 무거운 작업 금지 원칙). 차원이 바뀌거나(같은 차원이라도) 실제 World 인스턴스가 바뀌면
 * (재접속, 다른 서버 접속 등) 캐시 전체를 버린다 — 둘 다 완전히 다른 지형이기 때문. 아직
 * 클라이언트에 로드 안 된 청크는 캐시하지도 예산을 쓰지도 않는다(최종 리뷰에서 발견 — 로드
 * 전에 샘플링하면 world.getTopY가 getBottomY()를 그대로 돌려주고 그 자리 블록이
 * MapColor.CLEAR라 영구 검은 칸이 생겼다, Task 9가 고친 것과 같은 증상의 다른 원인). 캐시
 * 키는 ChunkCoord(순수 값 타입) — 실제 ChunkPos 변환은 기본 샘플러 어댑터 안, 게임이 실제로
 * 도는 순간에만 일어난다. */
public class MinimapChunkCache implements MinimapCompositor.ColumnColorLookup {
    private final BiFunction<World, ChunkCoord, int[]> sampler;
    private final BiPredicate<World, ChunkCoord> isLoaded;
    private final Map<ChunkCoord, int[]> colorsByChunk = new HashMap<>();
    private RegistryKey<World> lastDimension;
    private World lastWorld;

    public MinimapChunkCache() {
        this(
            (world, coord) -> ChunkColorSampler.sampleChunk(world, new ChunkPos(coord.x(), coord.z())),
            (world, coord) -> world.isChunkLoaded(coord.x(), coord.z())
        );
    }

    MinimapChunkCache(BiFunction<World, ChunkCoord, int[]> sampler, BiPredicate<World, ChunkCoord> isLoaded) {
        this.sampler = sampler;
        this.isLoaded = isLoaded;
    }

    public void tick(World world, RegistryKey<World> dimension, Set<ChunkCoord> neededChunks) {
        if (!dimension.equals(lastDimension) || world != lastWorld) {
            colorsByChunk.clear();
            lastDimension = dimension;
            lastWorld = world;
        }

        for (ChunkCoord coord : neededChunks) {
            if (colorsByChunk.containsKey(coord)) {
                continue;
            }
            if (!isLoaded.test(world, coord)) {
                // 아직 로드 안 됨 — 캐시하지도 예산을 쓰지도 않는다, 나중에 로드되면 그때 다시
                // 시도할 수 있어야 하므로.
                continue;
            }
            colorsByChunk.put(coord, sampler.apply(world, coord));
            return;
        }
    }

    @Override
    public int colorAt(int blockX, int blockZ) {
        ChunkCoord coord = new ChunkCoord(blockX >> 4, blockZ >> 4);
        int[] chunkColors = colorsByChunk.get(coord);
        if (chunkColors == null) {
            return 0;
        }
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        return chunkColors[localZ * 16 + localX];
    }
}
