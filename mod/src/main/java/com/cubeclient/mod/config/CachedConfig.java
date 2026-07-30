package com.cubeclient.mod.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.LongSupplier;

/**
 * ConfigStore를 감싸서 매 프레임 디스크를 읽지 않게 하는 인메모리 캐시. 이 모드 프로세스
 * 안에서 설정을 쓰는 경로는 save()뿐이므로, 캐시와 디스크가 어긋날 일이 없다.
 */
public class CachedConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedConfig.class);

    /**
     * current() is called once per rendered frame (see HudLayerRegistrationCallback in
     * CubeClientModClient), so a persistent disk fault must not turn into a disk read plus a
     * WARN log on every single frame. After a failed read we wait at least this long before
     * trying again, bounding both the I/O and the logging to roughly "once per cooldown window"
     * instead of "once per frame" for as long as the fault lasts.
     */
    static final long RETRY_COOLDOWN_MILLIS = 5_000;

    /** Sentinel meaning "no failure recorded yet" — never within the cooldown window. */
    private static final long NO_FAILURE = -1;

    private final ConfigStore store;
    private final LongSupplier clock;
    private ModConfig cached;
    private long lastFailureAtMillis = NO_FAILURE;

    public CachedConfig(ConfigStore store) {
        this(store, System::currentTimeMillis);
    }

    // Package-private: lets tests simulate the cooldown elapsing without a real sleep.
    CachedConfig(ConfigStore store, LongSupplier clock) {
        this.store = store;
        this.clock = clock;
    }

    public ModConfig current() {
        if (cached == null) {
            if (withinCooldown()) {
                return ModConfig.empty();
            }
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

    private boolean withinCooldown() {
        return lastFailureAtMillis != NO_FAILURE
            && clock.getAsLong() - lastFailureAtMillis < RETRY_COOLDOWN_MILLIS;
    }

    /**
     * @return the config read from disk, or {@code null} if the read failed. A {@code null}
     * return must NOT be cached — a transient I/O fault (e.g. a locked file) should eventually be
     * retried (after the cooldown above) rather than permanently frozen as ModConfig.empty() for
     * the rest of the process's lifetime, which would also risk that empty config being written
     * back over an intact on-disk file the next time save() runs.
     */
    private ModConfig loadOrNull() {
        try {
            return store.load();
        } catch (IOException e) {
            lastFailureAtMillis = clock.getAsLong();
            LOGGER.warn("Failed to read mod config from disk; using an empty config for now and " +
                "retrying after a short cooldown", e);
            return null;
        }
    }
}
