package com.cubeclient.mod.death;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLocationFilterTest {

    @Test
    void keepsOnlyMatchingWorldAndDimension() {
        DeathLocation match = new DeathLocation("singleplayer:World1", "minecraft:overworld", 1, 2, 3);
        DeathLocation wrongWorld = new DeathLocation("singleplayer:World2", "minecraft:overworld", 1, 2, 3);
        DeathLocation wrongDimension = new DeathLocation("singleplayer:World1", "minecraft:the_nether", 1, 2, 3);
        List<DeathLocation> all = List.of(match, wrongWorld, wrongDimension);

        List<DeathLocation> result = DeathLocationFilter.forCurrentWorld(all, "singleplayer:World1", "minecraft:overworld");

        assertEquals(1, result.size());
        assertTrue(result.contains(match));
    }

    @Test
    void emptyListStaysEmpty() {
        assertEquals(0, DeathLocationFilter.forCurrentWorld(List.of(), "any", "any").size());
    }

    @Test
    void multipleMatchesAllKept() {
        DeathLocation a = new DeathLocation("w", "d", 1, 2, 3);
        DeathLocation b = new DeathLocation("w", "d", 4, 5, 6);

        List<DeathLocation> result = DeathLocationFilter.forCurrentWorld(List.of(a, b), "w", "d");

        assertEquals(2, result.size());
    }
}
