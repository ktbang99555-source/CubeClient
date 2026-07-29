package com.cubeclient.mod.config;

import com.cubeclient.mod.gui.HudPosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The whole of what this mod persists: which feature ids are on, which are favorited, and (from
 * B1 onward) where and how large each feature draws itself. Still flat and Gson-friendly — no
 * nested objects or custom (de)serializer needed (HudPosition itself is just three double
 * fields, a record).
 */
public record ModConfig(
    Map<String, Boolean> enabled,
    Set<String> favorites,
    Map<String, HudPosition> positions
) {

    /**
     * Normalises what Gson hands back, because a config file is user-reachable and an older
     * version of this mod may have written a different shape.
     *
     * <p>Gson builds a record through its canonical constructor and substitutes nothing for a
     * field the JSON does not mention, so {@code {}} or a file carrying only {@code enabled}
     * arrives here with a null component — and the first {@link #isEnabled} call would crash the
     * game. Null becomes empty instead. {@code positions} gets the same treatment, for the same
     * reason.
     *
     * <p>The copy also settles a second inconsistency: {@link #empty()} hands out immutable
     * collections while Gson builds mutable ones, so whether a caller could mutate the config in
     * place depended on whether the player already had a config file. Now it never can, either
     * way. {@code LinkedHashMap}/{@code LinkedHashSet} rather than {@code Map.copyOf}/
     * {@code Set.copyOf} because those reject null values, which a hand-edited file can contain.
     */
    public ModConfig {
        enabled = enabled == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(enabled));
        favorites = favorites == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(favorites));
        positions = positions == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(positions));
    }

    public static ModConfig empty() {
        return new ModConfig(Map.of(), Set.of(), Map.of());
    }

    public boolean isEnabled(String featureId) {
        // Read into a Boolean first: a hand-edited file can hold an explicit null, and
        // unboxing that into this method's boolean return would throw.
        Boolean value = enabled.get(featureId);
        return value != null && value;
    }

    /** A feature that has never been dragged uses fallback (usually feature.defaultPosition()). */
    public HudPosition positionOr(String featureId, HudPosition fallback) {
        return positions.getOrDefault(featureId, fallback);
    }
}
