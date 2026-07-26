package com.cubeclient.launcher.profile;

import java.util.List;

public record Profile(String id, String mcVersion, String loader, List<String> mods) {}
