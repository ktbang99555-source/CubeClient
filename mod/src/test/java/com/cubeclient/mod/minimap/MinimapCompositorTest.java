// mod/src/test/java/com/cubeclient/mod/minimap/MinimapCompositorTest.java
package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void samePixelGridBucketProducesIdenticalOutputRegardlessOfSubBlockMovement() {
        // textureSize=2, radiusBlocks=2.0 -> blocksPerPixel=2.0. 0.0과 1.9는 같은 [0.0, 2.0)
        // 스냅 구간에 들어가므로, 그 사이 아무리 미세하게 움직여도 합성 결과가 완전히 같아야
        // 한다 — 안 그러면 픽셀마다 다른 시점에 값이 바뀌어 깜빡이는 노이즈가 생긴다
        // (실기기 피드백: "자글자글한 느낌").
        MinimapCompositor.ColumnColorLookup lookup = (bx, bz) -> (bx << 8) | (bz & 0xFF);
        int[] a = MinimapCompositor.composite(2, 2.0, 0.0, 0.0, lookup);
        int[] b = MinimapCompositor.composite(2, 2.0, 1.9, 1.9, lookup);

        assertArrayEquals(a, b);
    }
}
