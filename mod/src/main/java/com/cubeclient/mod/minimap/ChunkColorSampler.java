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

    /** 이 컬럼에선 색 있는 블록을 못 찾았다는 표식. 이런 컬럼은 검정(불투명)이 아니라
     * 0(투명 = "아직 모름")으로 남긴다 — 검정으로 칠하면 그게 진짜 지형색과 구분이 안 돼서
     * 캐시에 영구히 박히고, 실기기에서 재접속해야만 사라지는 검은 사각형이 됐다. */
    private static final int NO_COLORED_BLOCK = Integer.MIN_VALUE;

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                int topY = findColoredTopY(world, blockX, blockZ);
                if (topY == NO_COLORED_BLOCK) {
                    colors[localZ * 16 + localX] = 0;
                    continue;
                }
                int northTopY = findColoredTopY(world, blockX, blockZ - 1);

                BlockPos pos = new BlockPos(blockX, topY, blockZ);
                BlockState state = world.getBlockState(pos);
                MapColor mapColor = state.getMapColor(world, pos);

                // 북쪽 칸을 못 읽었으면(청크 경계 밖이 아직 미로드일 수 있다) 높이 비교를
                // 포기하고 평평하게 그린다 — 미로드를 "훨씬 낮음"으로 착각해 가짜 밝은 줄이
                // 청크 경계마다 생기는 걸 막는다.
                MapColor.Brightness brightness = northTopY == NO_COLORED_BLOCK ? MapColor.Brightness.NORMAL
                    : topY > northTopY ? MapColor.Brightness.HIGH
                    : topY < northTopY ? MapColor.Brightness.LOW
                    : MapColor.Brightness.NORMAL;

                int rgb = mapColor.getRenderColor(brightness) & 0x00FFFFFF;
                colors[localZ * 16 + localX] = rgb | 0xFF000000;
            }
        }
        return colors;
    }

    /** 색 있는(CLEAR가 아닌) 블록이 나올 때까지 아래로 내려간다. 다 내려가도 못 찾으면
     * NO_COLORED_BLOCK. 예전엔 여기서 마지막 y를 그냥 돌려줬는데, 그 자리 블록이 CLEAR면
     * 호출부가 그걸 그대로 색으로 써서 불투명 검정이 나왔다. */
    private static int findColoredTopY(World world, int blockX, int blockZ) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
        int floor = Math.max(world.getBottomY(), y - MAX_CLEAR_SKIP);
        while (y >= floor) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            MapColor mapColor = world.getBlockState(pos).getMapColor(world, pos);
            if (mapColor != MapColor.CLEAR) {
                return y;
            }
            y--;
        }
        return NO_COLORED_BLOCK;
    }
}
