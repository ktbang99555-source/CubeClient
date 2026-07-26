package com.cubeclient.launcher.manifest;

import java.util.List;

public record VersionDetail(
    String id,
    String mainClass,
    ClientDownload clientDownload,
    List<Library> libraries,
    AssetIndexRef assetIndex
) {
    public record ClientDownload(String url, String sha1, long size) {}
    public record Library(String relativePath, String url, String sha1, long size) {}
    public record AssetIndexRef(String id, String url, String sha1) {}
}
