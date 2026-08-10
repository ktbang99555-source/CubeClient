package com.cubeclient.mod.death;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 죽은 위치 목록을 mod-config.json과 별도 파일에 저장한다 — 기능 켜짐 설정과 성격이 다른
 * 데이터라서 ModConfig에 얹지 않는다. ConfigStore와 같은 실패 처리 패턴(빈 목록으로 취급,
 * JSON 손상 시 .bak으로 보존). */
public class DeathLocationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<DeathLocation>>() {}.getType();

    private final Path storeFile;
    private List<DeathLocation> cached;

    public DeathLocationStore(Path storeFile) {
        this.storeFile = storeFile;
    }

    public List<DeathLocation> getAll() {
        if (cached == null) {
            cached = loadOrEmpty();
        }
        return Collections.unmodifiableList(cached);
    }

    public void add(DeathLocation location) throws IOException {
        List<DeathLocation> updated = new ArrayList<>(getAll());
        updated.add(location);
        save(updated);
    }

    public void clearAll() throws IOException {
        save(new ArrayList<>());
    }

    private List<DeathLocation> loadOrEmpty() {
        if (!Files.exists(storeFile)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(storeFile);
            List<DeathLocation> loaded = GSON.fromJson(json, LIST_TYPE);
            if (loaded == null) {
                return new ArrayList<>();
            }
            // 손으로 편집한 JSON에 리스트 원소로 null이 섞여 있으면(예: "[null, {...}]")
            // Gson은 그걸 그대로 통과시킨다 — 나중에 DeathLocationFilter가 loc.worldId()를
            // 부르는 순간 NPE로 죽는다. 여기서 한 번 걸러내면 다른 모든 호출부가 안전해진다.
            loaded.removeIf(Objects::isNull);
            return loaded;
        } catch (IOException e) {
            return new ArrayList<>();
        } catch (JsonSyntaxException e) {
            backupCorruptFile();
            return new ArrayList<>();
        }
    }

    private void backupCorruptFile() {
        try {
            Path backup = storeFile.resolveSibling(storeFile.getFileName() + ".bak");
            Files.move(storeFile, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 백업조차 실패해도 빈 목록으로 계속 진행 — 크래시보다 낫다.
        }
    }

    private void save(List<DeathLocation> locations) throws IOException {
        if (storeFile.getParent() != null) {
            Files.createDirectories(storeFile.getParent());
        }
        // 임시 파일에 먼저 쓰고 옮긴다 — ConfigStore.save()와 같은 이유(저장 도중 죽으면 반쯤
        // 쓰인 JSON이 남아 다음 실행에서 손상 취급되어 죽은 위치 전부를 잃는다).
        Path tempFile = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
        Files.writeString(tempFile, GSON.toJson(locations, LIST_TYPE));
        try {
            Files.move(tempFile, storeFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, storeFile, StandardCopyOption.REPLACE_EXISTING);
        }
        cached = locations;
    }
}
