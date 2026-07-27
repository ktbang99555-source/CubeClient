package com.cubeclient.mod.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureRegistryTest {

    record TestFeature(String id, String displayName, Category category) implements Feature {}

    @Test
    void listReturnsEveryRegisteredFeatureWhenUnfiltered() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));

        List<Feature> result = registry.list(null, "", Set.of());

        assertEquals(2, result.size());
    }

    @Test
    void categoryFilterKeepsOnlyThatCategory() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));

        List<Feature> result = registry.list(Category.HUD, "", Set.of());

        assertEquals(List.of("fps"), result.stream().map(Feature::id).toList());
    }

    @Test
    void searchMatchesDisplayNameCaseInsensitively() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));
        registry.register(new TestFeature("cps", "CPS 표시", Category.HUD));

        List<Feature> result = registry.list(null, "fps", Set.of());

        assertEquals(List.of("fps"), result.stream().map(Feature::id).toList());
    }

    // A card's heart button always wins the sort, regardless of category filter or search —
    // otherwise a favorite could vanish from the top of the list just by switching tabs.
    @Test
    void favoritesSortToTheFrontRegardlessOfCategoryOrder() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));

        List<Feature> result = registry.list(null, "", Set.of("zoom"));

        assertEquals(List.of("zoom", "fps"), result.stream().map(Feature::id).toList());
    }

    @Test
    void withinTheSameFavoriteStatusSortsByCategoryThenName() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));
        registry.register(new TestFeature("cps", "CPS 표시", Category.HUD));
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));

        List<Feature> result = registry.list(null, "", Set.of());

        // Category enum order (HUD before CONTROL) first, then alphabetical within HUD.
        assertEquals(List.of("cps", "fps", "zoom"), result.stream().map(Feature::id).toList());
    }

    @Test
    void registeringTheSameIdTwiceIsRejected() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));

        assertThrows(IllegalArgumentException.class,
            () -> registry.register(new TestFeature("fps", "다른 이름", Category.HUD)));
    }

    @Test
    void anEmptyRegistryListsNothing() {
        FeatureRegistry registry = new FeatureRegistry();

        assertEquals(List.of(), registry.list(null, "", Set.of()));
    }
}
