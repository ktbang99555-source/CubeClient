package com.cubeclient.mod.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** 청크 하나의 16x16 컬럼을 훑어 지표면 최상단 블록의 지도 색을 뽑는다. 동굴(지하) 레이어는
 * 안 본다 — 바닐라 지도 아이템과 동일하게 WORLD_SURFACE 하이트맵만 쓴다. 인접한 칸(북쪽,
 * blockZ-1)과의 높이 차이로 밝기(LOW/NORMAL/HIGH)를 다르게 줘서 굴곡 음영을 낸다. */
public final class ChunkColorSampler {
    private ChunkColorSampler() {}

    // WORLD_SURFACE가 잡는 "맨 위 블록"은 꽃·잔디·눈 쌓임 같은 장식 블록도 포함하는데, 이런
    // 블록의 지도색은 MapColor.CLEAR(원본 색상값 0=검정)라 그대로 쓰면 검은 구멍이 생긴다.
    // 색 있는 블록이 나올 때까지 최대 이만큼만 아래로 내려간다(무한정 파면 청크 하나 처리
    // 비용이 너무 커진다 — 실기기에서 검은 구멍 버그로 발견됨).
    private static final int MAX_CLEAR_SKIP = 24;

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                int topY = findColoredTopY(world, blockX, blockZ);
                int northTopY = findColoredTopY(world, blockX, blockZ - 1);

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

    /** 색 있는(CLEAR가 아닌) 블록이 나올 때까지 아래로 내려간다. */
    private static int findColoredTopY(World world, int blockX, int blockZ) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
        int floor = Math.max(world.getBottomY(), y - MAX_CLEAR_SKIP);
        while (y > floor) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            MapColor mapColor = world.getBlockState(pos).getMapColor(world, pos);
            if (mapColor != MapColor.CLEAR) {
                return y;
            }
            y--;
        }
        return y;
    }
}
