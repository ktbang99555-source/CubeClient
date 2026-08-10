package com.cubeclient.mod.minimap;

/** 캐시된 청크 색상 데이터를 플레이어 중심 원형 이미지로 합성한다. Minecraft 객체에 의존하지
 * 않는다 — 실제 색상 조회는 ColumnColorLookup을 통해 주입받는다(MinimapChunkCache가 구현). */
public final class MinimapCompositor {
    private MinimapCompositor() {}

    @FunctionalInterface
    public interface ColumnColorLookup {
        /** blockX, blockZ 컬럼의 ARGB 색상. 아직 캐시되지 않은 청크는 0(완전 투명)을 반환해야 한다. */
        int colorAt(int blockX, int blockZ);
    }

    /** playerX/playerZ를 픽셀 격자(blocksPerPixel) 단위로 스냅한다 — 지형 합성과 엔티티 점이
     * 같은 기준점을 쓰게 하려고 공개 메서드로 뽑았다. 따로 계산하면 지형은 픽셀 단위로만
     * 갱신되는데 점은 매끄럽게 움직여서 서로 어긋나 보인다(최종 리뷰에서 발견). */
    public static double snapToPixelGrid(double coordinate, int textureSize, double radiusBlocks) {
        double blocksPerPixel = blocksPerPixel(textureSize, radiusBlocks);
        return Math.floor(coordinate / blocksPerPixel) * blocksPerPixel;
    }

    /** textureSize x textureSize 픽셀의 ARGB 배열(row-major, index = py*textureSize+px)을 만든다.
     * radiusBlocks는 실제 블록 단위 반경 — textureSize와 별개라서, 같은 반경을 더 크거나 작은
     * 텍스처로 렌더링할 수 있다(HUD 편집기의 배율 조절이 이걸 이용한다). 반경 밖 픽셀은 0. */
    public static int[] composite(int textureSize, double radiusBlocks, double playerX, double playerZ,
                                   ColumnColorLookup lookup) {
        int[] pixels = new int[textureSize * textureSize];
        double half = textureSize / 2.0;
        double blocksPerPixel = blocksPerPixel(textureSize, radiusBlocks);

        // 플레이어의 소수점 좌표를 그대로 쓰면, 픽셀마다 dxBlocks가 blocksPerPixel의 서로 다른
        // 배수라서 각자 다른 순간에 옆 블록으로 넘어가 깜빡이는 노이즈가 생긴다. 격자에
        // 스냅하면 기준점이 한 픽셀만큼 움직일 때만 바뀌고, 그 순간엔 모든 픽셀이 동시에
        // 밀려서 매끄럽게 스크롤한다.
        double snappedPlayerX = snapToPixelGrid(playerX, textureSize, radiusBlocks);
        double snappedPlayerZ = snapToPixelGrid(playerZ, textureSize, radiusBlocks);

        for (int py = 0; py < textureSize; py++) {
            double dzBlocks = (py + 0.5 - half) * blocksPerPixel;
            for (int px = 0; px < textureSize; px++) {
                double dxBlocks = (px + 0.5 - half) * blocksPerPixel;
                int index = py * textureSize + px;

                if (!MinimapMath.isColumnWithinRadius(dxBlocks, dzBlocks, radiusBlocks)) {
                    pixels[index] = 0;
                    continue;
                }

                int blockX = (int) Math.floor(snappedPlayerX + dxBlocks);
                int blockZ = (int) Math.floor(snappedPlayerZ + dzBlocks);
                pixels[index] = lookup.colorAt(blockX, blockZ);
            }
        }
        return pixels;
    }

    private static double blocksPerPixel(int textureSize, double radiusBlocks) {
        return radiusBlocks / (textureSize / 2.0);
    }
}
