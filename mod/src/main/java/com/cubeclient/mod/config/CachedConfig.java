package com.cubeclient.mod.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * ConfigStore를 감싸서 매 프레임 디스크를 읽지 않게 하는 인메모리 캐시. 이 모드 프로세스
 * 안에서 설정을 쓰는 경로는 save()뿐이므로, 캐시와 디스크가 어긋날 일이 없다.
 */
public class CachedConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedConfig.class);

    private final ConfigStore store;
    private ModConfig cached;

    public CachedConfig(ConfigStore store) {
        this.store = store;
    }

    public ModConfig current() {
        if (cached == null) {
            ModConfig loaded = loadOrNull();
            if (loaded != null) {
                cached = loaded;
            }
            return cached != null ? cached : ModConfig.empty();
        }
        return cached;
    }

    public void save(ModConfig config) throws IOException {
        store.save(config);
        cached = config;
    }

    /**
     * @return the config read from disk, or {@code null} if the read failed. A {@code null}
     * return must NOT be cached — a transient I/O fault (e.g. a locked file) should be retried
     * on the next current() call rather than permanently frozen as ModConfig.empty() for the
     * rest of the process's lifetime, which would also risk that empty config being written
     * back over an intact on-disk file the next time save() runs.
     */
    private ModConfig loadOrNull() {
        try {
            return store.load();
        } catch (IOException e) {
            LOGGER.warn("Failed to read mod config from disk; using an empty config for now and " +
                "retrying on next access", e);
            return null;
        }
    }
}
