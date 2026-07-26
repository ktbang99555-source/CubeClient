package com.cubeclient.launcher.manifest;

import java.util.List;

public record VersionDetail(
    String id,
    String mainClass,
    ClientDownload clientDownload,
    List<Library> libraries,
    AssetIndexRef assetIndex,
    /**
     * Java feature version this version requires, straight from the manifest.
     *
     * <p>Guessing this from the Minecraft version is a trap: 1.20.1 needs 17 but 1.21.4 needs 21,
     * and picking wrong makes the game die with {@code UnsupportedClassVersionError} before it
     * draws a frame. Mojang publishes the answer, so take it rather than infer it.
     */
    int javaMajorVersion
) {
    public record ClientDownload(String url, String sha1, long size) {}
    public record Library(String relativePath, String url, String sha1, long size) {}
    public record AssetIndexRef(String id, String url, String sha1) {}
}
