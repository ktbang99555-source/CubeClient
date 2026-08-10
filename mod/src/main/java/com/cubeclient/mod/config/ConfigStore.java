package com.cubeclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;

    public ConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    public ModConfig load() throws IOException {
        if (!Files.exists(configFile)) {
            return ModConfig.empty();
        }
        String json = Files.readString(configFile);
        try {
            ModConfig loaded = GSON.fromJson(json, ModConfig.class);
            return loaded == null ? ModConfig.empty() : loaded;
        } catch (JsonSyntaxException e) {
            // Preserved rather than deleted — mirrors the launcher's game-<timestamp>.log
            // rotation, which never destroys evidence of what went wrong.
            Path backup = configFile.resolveSibling(configFile.getFileName() + ".bak");
            Files.move(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
            return ModConfig.empty();
        }
    }

    public void save(ModConfig config) throws IOException {
        if (configFile.getParent() != null) {
            Files.createDirectories(configFile.getParent());
        }
        // 임시 파일에 먼저 쓰고 옮긴다 — 게임이 저장 도중 죽거나(크래시, 강제종료) 디스크가
        // 꽉 차면, configFile을 직접 write하는 경우 반쯤 쓰인 손상 JSON이 남아 다음 실행에서
        // load()가 그걸 .bak으로 보존하고 빈 설정으로 돌아간다 — 사용자의 실제 설정을 잃는다.
        // 임시 파일 쓰기가 중간에 실패해도 원본 configFile은 그대로 남는다.
        Path tempFile = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        Files.writeString(tempFile, GSON.toJson(config));
        try {
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 일부 파일시스템(네트워크 드라이브 등)은 원자적 이동을 지원하지 않는다 — 그 경우
            // 비원자적 이동으로 폴백(그래도 직접 write보다는 안전: 임시 파일이 완전히 다
            // 쓰인 뒤에만 옮긴다).
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * @param fallback used when the launcher did not set {@code -Dcubeclient.configDir} — the
     *                 mod was likely installed by hand into some other launcher. The caller
     *                 (Fabric-side, not this class) supplies Fabric's own per-instance config
     *                 directory as that fallback so this class stays runnable outside a
     *                 Minecraft runtime.
     */
    public static Path resolveConfigDir(Path fallback) {
        String configured = System.getProperty("cubeclient.configDir");
        return configured != null ? Path.of(configured) : fallback;
    }
}
