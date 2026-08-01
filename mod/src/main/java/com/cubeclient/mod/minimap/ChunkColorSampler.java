package com.cubeclient.mod.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** 청크 하나의 16x16 컬럼을 훑어 지표면 최상단 블록의 지도 색을 뽑는다. 동굴(지하) 레이어는
 * 안 본다 — 바닐라 지도 아이템과 동일하게 WORLD_SURFACE 하이트맵만 쓴다. 인접한 칸(북쪽,
 * blockZ-1)과의 높이 차이로 밝기(LOW/NORMAL/HIGH)를 다르게 줘서 굴곡 음영을 낸다 — 바닐라
 * 지도가 실제로 다채로워 보이는 이유가 이 음영이지 블록 색 종류가 아니다(실기기 검증에서
 * "너무 밍밍하다"는 피드백을 받고 추가됨). */
public final class ChunkColorSampler {
    private ChunkColorSampler() {}

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                // getTopY는 하이트맵 기준 "그 위 첫 공기 칸"을 돌려주는 것으로 추정 —
                // 실제 블록은 한 칸 아래.
                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
                int northTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ - 1) - 1;

                BlockPos pos = new BlockPos(blockX, topY, blockZ);
                BlockState state = world.getBlockState(pos);
                MapColor mapColor = state.getMapColor(world, pos);

                MapColor.Brightness brightness = topY > northTopY ? MapColor.Brightness.HIGH
                    : topY < northTopY ? MapColor.Brightness.LOW
                    : MapColor.Brightness.NORMAL;

                int rgb = mapColor.getRenderColor(brightness) & 0x00FFFFFF;
                colors[localZ * 16 + localX] = rgb | 0xFF000000;
            }
        }
        return colors;
    }
}
