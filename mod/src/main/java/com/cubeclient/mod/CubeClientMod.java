package com.cubeclient.mod;

import net.fabricmc.api.ModInitializer;

/**
 * Runs on both the client and (hypothetically) a dedicated server. Empty for now — everything
 * this modpack does is client-only rendering and screens, which belongs in
 * {@link CubeClientModClient} instead. Kept as a separate, real entrypoint rather than skipped
 * because {@code fabric.mod.json} already declares it; an empty implementation is clearer than
 * removing the entrypoint and leaving the declaration to point nowhere.
 */
public class CubeClientMod implements ModInitializer {
    @Override
    public void onInitialize() {
    }
}
