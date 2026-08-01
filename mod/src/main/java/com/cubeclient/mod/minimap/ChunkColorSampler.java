package com.cubeclient.mod.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** 청크 하나의 16x16 컬럼을 훑어 지표면 최상단 블록의 지도 색을 뽑는다. 동굴(지하) 레이어는
 * 안 본다 — 바닐라 지도 아이템과 동일하게 WORLD_SURFACE 하이트맵만 쓴다. */
public final class ChunkColorSampler {
    private ChunkColorSampler() {}

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                // getTopY는 하이트맵 기준 "그 위 첫 공기 칸"을 돌려주는 것으로 추정 —
                // 실제 블록은 한 칸 아래. Task 7에서 시각적으로 어긋나면 이 -1을 다시 확인한다.
                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
                BlockPos pos = new BlockPos(blockX, topY, blockZ);
                BlockState state = world.getBlockState(pos);
                MapColor mapColor = state.getMapColor(world, pos);
                int rgb = mapColor.getRenderColor(MapColor.Brightness.NORMAL) & 0x00FFFFFF;
                colors[localZ * 16 + localX] = rgb | 0xFF000000;
            }
        }
        return colors;
    }
}
