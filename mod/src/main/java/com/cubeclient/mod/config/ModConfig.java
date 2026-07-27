package com.cubeclient.mod.config;

import java.util.Map;
import java.util.Set;

/**
 * The whole of what this mod persists: which feature ids are on, and which are favorited.
 * Deliberately flat and Gson-friendly — no nested objects, no custom (de)serializer needed.
 */
public record ModConfig(Map<String, Boolean> enabled, Set<String> favorites) {
    public static ModConfig empty() {
        return new ModConfig(Map.of(), Set.of());
    }

    public boolean isEnabled(String featureId) {
        return enabled.getOrDefault(featureId, false);
    }
}
