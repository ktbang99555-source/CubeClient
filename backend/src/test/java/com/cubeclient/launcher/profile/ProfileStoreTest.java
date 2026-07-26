package com.cubeclient.launcher.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadAllReturnsEmptyListWhenFileMissing() throws IOException {
        ProfileStore store = new ProfileStore(tempDir.resolve("profiles.json"));
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void saveAllThenLoadAllRoundTrips() throws IOException {
        Path path = tempDir.resolve("profiles.json");
        ProfileStore store = new ProfileStore(path);
        List<Profile> profiles = List.of(
            new Profile("latest-1.21", "1.21.4", "fabric", List.of("minimap", "fps-hud", "serverlist")),
            new Profile("hypixel-1.8.9", "1.8.9", "legacyfabric", List.of("minimap", "fps-hud", "serverlist"))
        );

        store.saveAll(profiles);
        List<Profile> loaded = store.loadAll();

        assertEquals(profiles, loaded);
    }
}
