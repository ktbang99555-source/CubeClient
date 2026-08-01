// mod/src/main/java/com/cubeclient/mod/minimap/MinimapCompositor.java
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

    /** textureSize x textureSize 픽셀의 ARGB 배열(row-major, index = py*textureSize+px)을 만든다.
     * radiusBlocks는 실제 블록 단위 반경 — textureSize와 별개라서, 같은 반경을 더 크거나 작은
     * 텍스처로 렌더링할 수 있다(HUD 편집기의 배율 조절이 이걸 이용한다). 반경 밖 픽셀은 0. */
    public static int[] composite(int textureSize, double radiusBlocks, double playerX, double playerZ,
                                   ColumnColorLookup lookup) {
        int[] pixels = new int[textureSize * textureSize];
        double half = textureSize / 2.0;
        double blocksPerPixel = radiusBlocks / half;

        for (int py = 0; py < textureSize; py++) {
            double dzBlocks = (py + 0.5 - half) * blocksPerPixel;
            for (int px = 0; px < textureSize; px++) {
                double dxBlocks = (px + 0.5 - half) * blocksPerPixel;
                int index = py * textureSize + px;

                if (!MinimapMath.isColumnWithinRadius(dxBlocks, dzBlocks, radiusBlocks)) {
                    pixels[index] = 0;
                    continue;
                }

                int blockX = (int) Math.floor(playerX + dxBlocks);
                int blockZ = (int) Math.floor(playerZ + dzBlocks);
                pixels[index] = lookup.colorAt(blockX, blockZ);
            }
        }
        return pixels;
    }
}
