package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
