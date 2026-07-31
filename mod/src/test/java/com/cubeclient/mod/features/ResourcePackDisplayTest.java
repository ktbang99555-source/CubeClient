package com.cubeclient.mod.features;

import net.minecraft.resource.ResourcePackSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackDisplayTest {

    @Test
    void noEnabledPacksShowsNoneMessage() {
        String line = ResourcePackDisplay.formatLine(List.of());

        assertEquals("리소스팩 없음", line);
    }

    @Test
    void singlePackShowsItsName() {
        String line = ResourcePackDisplay.formatLine(List.of("Faithful"));

        assertEquals("Faithful", line);
    }

    @Test
    void multiplePacksAreCommaJoined() {
        String line = ResourcePackDisplay.formatLine(List.of("Faithful", "Sound Pack"));

        assertEquals("Faithful, Sound Pack", line);
    }

    @Test
    void requiredPackWithNoSourceIsHidden() {
        assertFalse(ResourcePackDisplay.isUserVisiblePack(true, ResourcePackSource.NONE, "fabric-api"));
    }

    @Test
    void requiredPackFromServerIsVisible() {
        assertTrue(ResourcePackDisplay.isUserVisiblePack(true, ResourcePackSource.SERVER, "server_pack"));
    }

    @Test
    void requiredPackFromWorldIsVisible() {
        assertTrue(ResourcePackDisplay.isUserVisiblePack(true, ResourcePackSource.WORLD, "world_pack"));
    }

    @Test
    void nonRequiredPackIsVisible() {
        assertTrue(ResourcePackDisplay.isUserVisiblePack(false, ResourcePackSource.NONE, "programmer_art"));
    }

    @Test
    void vanillaIsHiddenRegardlessOfRequiredOrSource() {
        assertFalse(ResourcePackDisplay.isUserVisiblePack(false, ResourcePackSource.NONE, "vanilla"));
    }
}
