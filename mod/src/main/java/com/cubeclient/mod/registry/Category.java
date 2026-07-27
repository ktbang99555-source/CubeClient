package com.cubeclient.mod.registry;

/**
 * Enum declaration order is the sort order the mod-list screen's category column uses, and also
 * the tab order across the top of the screen (전부 is not a member — it's "no filter", handled
 * by passing {@code null} to {@link FeatureRegistry#list}).
 */
public enum Category {
    HUD("HUD"),
    CONTROL("조작"),
    WORLD("월드"),
    SERVER("서버");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
