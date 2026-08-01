package com.cubeclient.mod.minimap;

import java.util.ArrayList;
import java.util.List;

/** 미니맵 반경 계산에 쓰는 순수 좌표 수학. Minecraft 월드 상태에 의존하지 않는다. */
public final class MinimapMath {
    private MinimapMath() {}

    /** dx, dz는 중심으로부터의 블록 단위 오프셋(어느 축이든 동일 단위면 픽셀 오프셋에도 그대로 쓸 수 있다). */
    public static boolean isColumnWithinRadius(double dx, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** 플레이어 위치를 중심으로 반경을 덮는 데 필요한 청크 좌표 전부(사각 경계 근사, 모서리
     * 청크 몇 개가 실제로는 반경 밖이어도 포함될 수 있다 — MinimapCompositor가 픽셀 단위로
     * 다시 걸러내므로 여기선 손해가 없다). */
    public static List<ChunkCoord> chunksInRadius(double playerX, double playerZ, double radius) {
        int minChunkX = (int) Math.floor((playerX - radius) / 16.0);
        int maxChunkX = (int) Math.floor((playerX + radius) / 16.0);
        int minChunkZ = (int) Math.floor((playerZ - radius) / 16.0);
        int maxChunkZ = (int) Math.floor((playerZ + radius) / 16.0);

        List<ChunkCoord> result = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                result.add(new ChunkCoord(cx, cz));
            }
        }
        return result;
    }
}
