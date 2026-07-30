package com.cubeclient.mod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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

    // 위 테스트는 malformed JSON을 검증하지만, 그 경로는 ConfigStore.load()가 내부적으로
    // JsonSyntaxException을 흡수하고 정상적으로 ModConfig.empty()를 반환하는 경로라 CachedConfig의
    // catch(IOException) 블록을 실제로 거치지 않는다. 여기서는 configFile 자리에 디렉터리를 둬서
    // Files.readString()이 진짜 IOException을 던지게 만들어 그 catch 블록을 직접 검증하고,
    // 그 실패가 current()의 캐시로 영구히 굳지 않는다는 것도 함께 검증한다 — 디스크를 고치고
    // save()로 실제 값을 쓴 뒤 current()를 다시 부르면 그 실제 값을 돌려줘야 한다.
    @Test
    void aGenuineIoExceptionOnFirstAccessIsNotPermanentlyCachedAndRetriesOnNextCall() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.createDirectory(file); // a directory, not a file — Files.readString() throws IOException on this
        ConfigStore store = new ConfigStore(file);
        CachedConfig cached = new CachedConfig(store);

        // First call: the disk read genuinely throws IOException. current() must still return a
        // usable config rather than propagating the exception.
        assertTrue(cached.current().enabled().isEmpty());

        // Fix the disk (replace the directory with a real file) and save real data through the
        // same store — simulating the transient fault clearing up.
        Files.delete(file);
        store.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        // The next current() call must pick up the real saved value, proving the earlier
        // failure was not cached as the permanent truth for the rest of the session.
        assertTrue(cached.current().isEnabled("fps"));
    }
}
