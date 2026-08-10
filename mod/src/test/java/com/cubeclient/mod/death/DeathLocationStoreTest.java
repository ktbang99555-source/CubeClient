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

    @Test
    void nullElementsInHandEditedJsonAreDroppedNotCrashed(@TempDir Path tempDir) throws IOException {
        // 손으로 편집한 파일에 "[null, {...}]"처럼 null 원소가 섞여도, 나머지 항목은 정상
        // 읽히고 null만 조용히 제거돼야 한다 — 원래는 DeathLocationFilter가 loc.worldId()를
        // 부르는 순간 NPE로 죽었다.
        Path file = tempDir.resolve("death-locations.json");
        Files.writeString(file,
            "[null, {\"worldId\":\"w\",\"dimensionId\":\"minecraft:overworld\",\"x\":1.0,\"y\":64.0,\"z\":2.0}]");

        DeathLocationStore store = new DeathLocationStore(file);

        assertEquals(1, store.getAll().size());
        assertEquals("w", store.getAll().get(0).worldId());
    }

    @Test
    void addSurvivesAcrossMultipleSavesNotJustTheFirst(@TempDir Path tempDir) throws IOException {
        // 임시파일+원자적 이동으로 저장 방식이 바뀐 뒤에도, 같은 인스턴스로 여러 번 저장하는
        // 반복 사용(임시 파일 재사용, 두 번째부터의 REPLACE_EXISTING 경로)이 안전한지 확인.
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));

        store.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));
        store.add(new DeathLocation("w", "minecraft:the_nether", 3.0, 65.0, 4.0));

        assertEquals(2, store.getAll().size());
    }
}
