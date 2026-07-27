package com.cubeclient.mod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadingAMissingFileReturnsAnEmptyConfig() throws IOException {
        ConfigStore store = new ConfigStore(tempDir.resolve("mod-config.json"));

        ModConfig loaded = store.load();

        assertTrue(loaded.enabled().isEmpty());
        assertTrue(loaded.favorites().isEmpty());
    }

    @Test
    void savedConfigRoundTripsThroughLoad() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        ModConfig original = new ModConfig(Map.of("fps", true), Set.of("fps"));

        store.save(original);
        ModConfig loaded = store.load();

        assertEquals(Map.of("fps", true), loaded.enabled());
        assertEquals(Set.of("fps"), loaded.favorites());
    }

    // Deleting a feature in a later version must not corrupt the config file for everyone who
    // has it in their enabled map — this is what lets B1-B5 add and remove features freely.
    @Test
    void unknownIdsInTheFileLoadWithoutError() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "some-future-feature": true }, "favorites": [] }
            """);
        ConfigStore store = new ConfigStore(file);

        ModConfig loaded = store.load();

        assertEquals(true, loaded.enabled().get("some-future-feature"));
    }

    @Test
    void aCorruptFileIsMovedAsideRatherThanCrashingOrBeingDeleted() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, "{ not valid json");
        ConfigStore store = new ConfigStore(file);

        ModConfig loaded = store.load();

        assertTrue(loaded.enabled().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("mod-config.json.bak")),
            "the corrupt file should be preserved, not deleted");
        assertEquals("{ not valid json", Files.readString(tempDir.resolve("mod-config.json.bak")));
    }

    // A config written by an older version of the mod, or hand-edited, can be missing a key
    // entirely. Gson builds records through the canonical constructor and substitutes nothing
    // for an absent field, so without normalisation these arrive null and the first isEnabled
    // call crashes the game.
    @Test
    void aConfigMissingKeysEntirelyLoadsAsEmptyRatherThanNull() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, "{}");

        ModConfig loaded = new ConfigStore(file).load();

        assertNotNull(loaded.enabled(), "a missing 'enabled' key must not become null");
        assertNotNull(loaded.favorites(), "a missing 'favorites' key must not become null");
        assertFalse(loaded.isEnabled("fps"));
    }

    @Test
    void aConfigWithOnlyOneOfTheTwoKeysLoadsCleanly() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "fps": true } }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertTrue(loaded.isEnabled("fps"));
        assertTrue(loaded.favorites().isEmpty());
    }

    // A hand-edited file can carry a null value. Unboxing that into isEnabled's boolean return
    // would throw, so an absent value has to read as "off".
    @Test
    void aNullValueInsideEnabledReadsAsOff() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "fps": null }, "favorites": [] }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertFalse(loaded.isEnabled("fps"));
    }

    // ModConfig.empty() hands out immutable Map.of()/Set.of(), but Gson builds mutable
    // collections when it deserialises a file. Callers must not have to know which one they got
    // — otherwise code that works for a returning user throws for a fresh install, or vice versa.
    @Test
    void loadedCollectionsAreImmutableJustLikeAnEmptyConfig() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "fps": true }, "favorites": ["fps"] }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertThrows(UnsupportedOperationException.class, () -> loaded.enabled().put("cps", true));
        assertThrows(UnsupportedOperationException.class, () -> loaded.favorites().add("cps"));
    }

    @Test
    void savingCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("nested").resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);

        store.save(new ModConfig(Map.of(), Set.of()));

        assertTrue(Files.exists(file));
    }

    @Test
    void resolveConfigDirUsesTheSystemPropertyWhenSet() {
        System.setProperty("cubeclient.configDir", tempDir.toString());
        try {
            Path resolved = ConfigStore.resolveConfigDir(Path.of("unused-fallback"));
            assertEquals(tempDir, resolved);
        } finally {
            System.clearProperty("cubeclient.configDir");
        }
    }

    @Test
    void resolveConfigDirFallsBackWhenThePropertyIsAbsent() {
        System.clearProperty("cubeclient.configDir");

        Path resolved = ConfigStore.resolveConfigDir(tempDir);

        assertEquals(tempDir, resolved);
    }
}
