package com.cubeclient.mod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void currentReadsFromDiskOnFirstCall() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        store.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        CachedConfig cached = new CachedConfig(store);

        assertTrue(cached.current().isEnabled("fps"));
    }

    // 여러 HUD 기능이 프레임마다 각자 디스크를 읽는 걸 막는 게 이 클래스의 목적이므로, 디스크를
    // 우회해서 파일을 바꿔도 save()를 거치지 않으면 current()가 옛 값을 계속 돌려줘야 한다 —
    // 캐시가 실제로 캐시 역할을 하는지 증명한다.
    @Test
    void currentDoesNotReReadDiskAfterTheFirstCall() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        store.save(new ModConfig(Map.of("fps", false), Set.of(), Map.of()));
        CachedConfig cached = new CachedConfig(store);
        cached.current();

        store.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        assertEquals(false, cached.current().isEnabled("fps"));
    }

    @Test
    void saveWritesToDiskAndUpdatesTheCacheImmediately() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        CachedConfig cached = new CachedConfig(store);

        cached.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        assertTrue(cached.current().isEnabled("fps"));
        assertTrue(new ConfigStore(file).load().isEnabled("fps"));
    }

    @Test
    void aBrokenDiskReadOnFirstAccessFallsBackToEmptyRatherThanThrowing() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        java.nio.file.Files.writeString(file, "{ not valid json");
        CachedConfig cached = new CachedConfig(new ConfigStore(file));

        assertTrue(cached.current().enabled().isEmpty());
    }
}
