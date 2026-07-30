package com.cubeclient.mod.config;

import java.io.IOException;

/**
 * ConfigStore를 감싸서 매 프레임 디스크를 읽지 않게 하는 인메모리 캐시. 이 모드 프로세스
 * 안에서 설정을 쓰는 경로는 save()뿐이므로, 캐시와 디스크가 어긋날 일이 없다.
 */
public class CachedConfig {
    private final ConfigStore store;
    private ModConfig cached;

    public CachedConfig(ConfigStore store) {
        this.store = store;
    }

    public ModConfig current() {
        if (cached == null) {
            cached = loadOrEmpty();
        }
        return cached;
    }

    public void save(ModConfig config) throws IOException {
        store.save(config);
        cached = config;
    }

    private ModConfig loadOrEmpty() {
        try {
            return store.load();
        } catch (IOException e) {
            return ModConfig.empty();
        }
    }
}
