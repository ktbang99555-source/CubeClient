package com.cubeclient.mod.registry;

/**
 * One toggleable entry in the mod-list screen. Implementations are pure metadata plus the
 * category they belong to — the actual on/off behaviour lives wherever the feature hooks into
 * the game (a {@code HudRenderCallback}, a keybinding, etc.), not here. Keeping this interface
 * small is what lets {@link FeatureRegistryTest} test sorting and filtering without touching
 * Minecraft at all.
 */
public interface Feature {
    String id();
    String displayName();
    Category category();
}
