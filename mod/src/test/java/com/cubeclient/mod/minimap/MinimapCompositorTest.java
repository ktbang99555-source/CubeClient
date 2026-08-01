// mod/src/test/java/com/cubeclient/mod/minimap/MinimapCompositorTest.java
package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapCompositorTest {

    @Test
    void pixelsOutsideRadiusAreTransparentAndInsideAreOpaque() {
        // textureSize=4, radiusBlocks=2 -> blocksPerPixel=1. 네 모서리 픽셀만 반경 밖.
        int[] pixels = MinimapCompositor.composite(4, 2.0, 100.0, 200.0, (bx, bz) -> 0xFF112233);

        assertEquals(0, pixels[0]);  // (px=0, py=0) 좌상단 모서리
        assertEquals(0, pixels[3]);  // (px=3, py=0) 우상단 모서리
        assertEquals(0, pixels[12]); // (px=0, py=3) 좌하단 모서리
        assertEquals(0, pixels[15]); // (px=3, py=3) 우하단 모서리
        assertEquals(0xFF112233, pixels[5]);  // (px=1, py=1) 중심 근처
        assertEquals(0xFF112233, pixels[6]);  // (px=2, py=1)

        long opaqueCount = Arrays.stream(pixels).filter(p -> p != 0).count();
        assertEquals(12, opaqueCount);
    }

    @Test
    void lookupReceivesCorrectAbsoluteBlockCoordinates() {
        // textureSize=2, radiusBlocks=2 -> blocksPerPixel=2. 반경이 커서 클리핑 없음(4칸 전부 안).
        // playerX=0, playerZ=0이라 블록 좌표가 그대로 오프셋과 같다.
        int[] pixels = MinimapCompositor.composite(2, 2.0, 0.0, 0.0,
            (bx, bz) -> (bx + 10) * 100 + (bz + 10));

        assertEquals(909, pixels[0]);  // px=0,py=0 -> blockX=-1, blockZ=-1
        assertEquals(1109, pixels[1]); // px=1,py=0 -> blockX=1,  blockZ=-1
        assertEquals(911, pixels[2]);  // px=0,py=1 -> blockX=-1, blockZ=1
        assertEquals(1111, pixels[3]); // px=1,py=1 -> blockX=1,  blockZ=1
    }
}
