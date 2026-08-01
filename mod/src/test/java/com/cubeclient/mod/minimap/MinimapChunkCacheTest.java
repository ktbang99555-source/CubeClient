package com.cubeclient.mod.minimap;

import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapChunkCacheTest {

    @Test
    void refreshesAtMostOneChunkPerTick() {
        int[] calls = {0};
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> {
            calls[0]++;
            return new int[256];
        }, (world, coord) -> true);
        Set<ChunkCoord> needed = new LinkedHashSet<>();
        needed.add(new ChunkCoord(0, 0));
        needed.add(new ChunkCoord(1, 0));

        cache.tick(null, World.OVERWORLD, needed);
        assertEquals(1, calls[0]);

        cache.tick(null, World.OVERWORLD, needed);
        assertEquals(2, calls[0]);

        // 이미 둘 다 채워졌으니 세 번째 틱은 다시 샘플링하지 않는다.
        cache.tick(null, World.OVERWORLD, needed);
        assertEquals(2, calls[0]);
    }

    @Test
    void colorAtReturnsSampledValueAfterTick() {
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> {
            int[] colors = new int[256];
            colors[0] = 0xFFAABBCC; // localX=0, localZ=0
            return colors;
        }, (world, coord) -> true);

        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));

        assertEquals(0xFFAABBCC, cache.colorAt(0, 0));
    }

    @Test
    void colorAtReturnsTransparentForUncachedChunk() {
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> new int[256], (world, coord) -> true);

        assertEquals(0, cache.colorAt(500, 500));
    }

    @Test
    void dimensionChangeClearsCache() {
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> {
            int[] colors = new int[256];
            colors[0] = 0xFFAABBCC;
            return colors;
        }, (world, coord) -> true);
        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));
        assertEquals(0xFFAABBCC, cache.colorAt(0, 0));

        // 차원이 바뀌면 캐시가 비워진다 — 새로 채워지기 전까진 다시 미탐사(투명) 취급.
        cache.tick(null, World.NETHER, Set.of());
        assertEquals(0, cache.colorAt(0, 0));
    }

    @Test
    void unloadedChunkIsSkippedNotCachedAndDoesNotConsumeBudget() {
        // 최종 리뷰에서 발견된 버그 재현: 아직 로드 안 된 청크를 그대로 샘플링하면 실제
        // 게임에서 영구 검은 칸이 남았다. isLoaded가 false를 주면 샘플러가 아예 안 불려야
        // 하고(예산 안 씀), 캐시에도 안 남아야 한다(나중에 로드되면 다시 시도해야 하므로).
        int[] calls = {0};
        MinimapChunkCache cache = new MinimapChunkCache(
            (world, coord) -> {
                calls[0]++;
                return new int[256];
            },
            (world, coord) -> false
        );

        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));

        assertEquals(0, calls[0]);
        assertEquals(0, cache.colorAt(0, 0));
    }

    @Test
    void chunkIsSampledOnceItBecomesLoaded() {
        boolean[] loaded = {false};
        int[] calls = {0};
        MinimapChunkCache cache = new MinimapChunkCache(
            (world, coord) -> {
                calls[0]++;
                int[] colors = new int[256];
                colors[0] = 0xFFAABBCC;
                return colors;
            },
            (world, coord) -> loaded[0]
        );

        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));
        assertEquals(0, calls[0]);
        assertEquals(0, cache.colorAt(0, 0));

        loaded[0] = true;
        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));
        assertEquals(1, calls[0]);
        assertEquals(0xFFAABBCC, cache.colorAt(0, 0));
    }
}
