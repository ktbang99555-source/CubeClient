package com.cubeclient.mod.death;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLocationStoreTest {

    @Test
    void emptyStoreWithNoFileReturnsEmptyList(@TempDir Path tempDir) {
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));

        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void addPersistsAndGetAllReflectsIt(@TempDir Path tempDir) throws IOException {
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));

        store.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));

        assertEquals(1, store.getAll().size());
        assertEquals("w", store.getAll().get(0).worldId());
    }

    @Test
    void newStoreInstanceReadsWhatAPreviousInstanceWrote(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("death-locations.json");
        DeathLocationStore first = new DeathLocationStore(file);
        first.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));

        DeathLocationStore second = new DeathLocationStore(file);

        assertEquals(1, second.getAll().size());
    }

    @Test
    void clearAllEmptiesTheStore(@TempDir Path tempDir) throws IOException {
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));
        store.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));

        store.clearAll();

        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void corruptFileIsTreatedAsEmptyAndBackedUp(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("death-locations.json");
        Files.writeString(file, "{ not valid json [");

        DeathLocationStore store = new DeathLocationStore(file);

        assertTrue(store.getAll().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("death-locations.json.bak")));
    }
}
