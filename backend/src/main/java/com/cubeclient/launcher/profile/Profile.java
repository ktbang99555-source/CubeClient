package com.cubeclient.launcher.profile;

import java.util.List;

/**
 * One launchable configuration.
 *
 * @param loader either {@code vanilla} or {@code fabric}. Legacy Fabric was removed with 1.8.9
 *               support; see {@code LoaderInstaller}.
 */
public record Profile(String id, String mcVersion, String loader, List<String> mods) {}
