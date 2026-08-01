package com.cubeclient.mod.minimap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** 청크별 색상 배열을 캐시하고, 매 tick() 호출마다 최대 1개 청크만 새로 샘플링한다(매 프레임
 * 무거운 작업 금지 원칙). 차원이 바뀌면 캐시 전체를 버린다 — 같은 청크 좌표라도 완전히 다른
 * 지형이기 때문. 캐시 키는 ChunkCoord(순수 값 타입) — 실제 ChunkPos 변환은 기본 샘플러
 * 어댑터 안, 게임이 실제로 도는 순간에만 일어난다. */
public class MinimapChunkCache implements MinimapCompositor.ColumnColorLookup {
    private final BiFunction<World, ChunkCoord, int[]> sampler;
    private final Map<ChunkCoord, int[]> colorsByChunk = new HashMap<>();
    private RegistryKey<World> lastDimension;

    public MinimapChunkCache() {
        this((world, coord) -> ChunkColorSampler.sampleChunk(world, new ChunkPos(coord.x(), coord.z())));
    }

    MinimapChunkCache(BiFunction<World, ChunkCoord, int[]> sampler) {
        this.sampler = sampler;
    }

    public void tick(World world, RegistryKey<World> dimension, Set<ChunkCoord> neededChunks) {
        if (!dimension.equals(lastDimension)) {
            colorsByChunk.clear();
            lastDimension = dimension;
        }

        for (ChunkCoord coord : neededChunks) {
            if (!colorsByChunk.containsKey(coord)) {
                colorsByChunk.put(coord, sampler.apply(world, coord));
                return;
            }
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
