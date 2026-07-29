package com.cubeclient.mod.config;

import com.cubeclient.mod.gui.HudPosition;
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
        ModConfig original = new ModConfig(Map.of("fps", true), Set.of("fps"), Map.of());

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

        store.save(new ModConfig(Map.of(), Set.of(), Map.of()));

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

    // Gson이 없는 필드를 null로 채우는 문제(ModConfig의 기존 두 필드에서 이미 겪음)가
    // positions 필드에도 똑같이 적용되는지 확인한다.
    @Test
    void aConfigMissingThePositionsKeyLoadsWithAnEmptyPositionsMap() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "fps": true }, "favorites": [] }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertNotNull(loaded.positions(), "a missing 'positions' key must not become null");
        assertTrue(loaded.positions().isEmpty());
    }

    @Test
    void positionOrReturnsTheStoredPositionWhenPresent() {
        HudPosition stored = HudPosition.of(0.2, 0.3, 1.0);
        ModConfig config = new ModConfig(Map.of(), Set.of(), Map.of("speed", stored));

        HudPosition result = config.positionOr("speed", HudPosition.of(0.0, 0.0, 1.0));

        assertEquals(stored, result);
    }

    @Test
    void positionOrReturnsTheFallbackWhenNothingIsStoredForThatId() {
        ModConfig config = ModConfig.empty();
        HudPosition fallback = HudPosition.of(0.1, 0.1, 1.0);

        HudPosition result = config.positionOr("speed", fallback);

        assertEquals(fallback, result);
    }

    @Test
    void positionsMapIsImmutableJustLikeTheOtherTwoFields() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": {}, "favorites": [], "positions": { "speed": { "xRatio": 0.1, "yRatio": 0.1, "scale": 1.0 } } }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertThrows(UnsupportedOperationException.class,
            () -> loaded.positions().put("cps", HudPosition.of(0.0, 0.0, 1.0)));
    }
}
