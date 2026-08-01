package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapMathTest {

    @Test
    void centerIsAlwaysWithinRadius() {
        assertTrue(MinimapMath.isColumnWithinRadius(0, 0, 10));
    }

    @Test
    void exactBoundaryIsWithinRadius() {
        assertTrue(MinimapMath.isColumnWithinRadius(10, 0, 10));
    }

    @Test
    void justOutsideBoundaryIsExcluded() {
        assertFalse(MinimapMath.isColumnWithinRadius(10.1, 0, 10));
    }

    @Test
    void diagonalDistanceUsesPythagoras() {
        // sqrt(8^2 + 8^2) = 11.31 > 10
        assertFalse(MinimapMath.isColumnWithinRadius(8, 8, 10));
    }

    @Test
    void chunksInRadiusCoversExpectedBoundingBox() {
        // playerX=0, playerZ=0, radius=20 -> chunk index range floor(-20/16)..floor(20/16) = -2..1 (4칸) 양축
        List<ChunkCoord> chunks = MinimapMath.chunksInRadius(0, 0, 20);

        assertEquals(16, chunks.size());
        assertTrue(chunks.contains(new ChunkCoord(-2, -2)));
        assertTrue(chunks.contains(new ChunkCoord(1, 1)));
        assertFalse(chunks.contains(new ChunkCoord(2, 0)));
    }
}
