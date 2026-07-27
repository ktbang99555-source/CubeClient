package com.cubeclient.mod.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FeatureRegistry {
    private final Map<String, Feature> byId = new LinkedHashMap<>();

    public void register(Feature feature) {
        if (byId.containsKey(feature.id())) {
            throw new IllegalArgumentException("Feature already registered: " + feature.id());
        }
        byId.put(feature.id(), feature);
    }

    public List<Feature> all() {
        return List.copyOf(byId.values());
    }

    /**
     * @param categoryFilter null means "전부" — no category restriction
     * @param searchText     matched against displayName, case-insensitively, substring match
     * @param favoriteIds    ids to sort to the front, ahead of category/name order
     */
    public List<Feature> list(Category categoryFilter, String searchText, Set<String> favoriteIds) {
        String needle = searchText == null ? "" : searchText.toLowerCase(Locale.ROOT);

        List<Feature> result = new ArrayList<>(byId.values());
        result.removeIf(f -> categoryFilter != null && f.category() != categoryFilter);
        result.removeIf(f -> !f.displayName().toLowerCase(Locale.ROOT).contains(needle));

        result.sort(
            Comparator
                .comparing((Feature f) -> !favoriteIds.contains(f.id()))
                .thenComparing(f -> f.category().ordinal())
                .thenComparing(Feature::displayName)
        );
        return result;
    }
}
