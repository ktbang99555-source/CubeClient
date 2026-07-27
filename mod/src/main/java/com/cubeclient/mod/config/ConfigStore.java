package com.cubeclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
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
        Files.writeString(configFile, GSON.toJson(config));
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
