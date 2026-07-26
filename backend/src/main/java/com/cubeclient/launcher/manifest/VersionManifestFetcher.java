package com.cubeclient.launcher.manifest;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VersionManifestFetcher {
    private static final String MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final HttpFetcher fetcher;

    public VersionManifestFetcher(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public List<VersionEntry> fetchVersionList() throws IOException {
        String body = fetcher.getString(MANIFEST_URL);
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray versionsArray = root.getAsJsonArray("versions");
            List<VersionEntry> versions = new ArrayList<>();
            for (int i = 0; i < versionsArray.size(); i++) {
                JsonObject entry = versionsArray.get(i).getAsJsonObject();
                versions.add(new VersionEntry(entry.get("id").getAsString(), entry.get("url").getAsString()));
            }
            return versions;
        } catch (RuntimeException e) {
            throw new IOException("Malformed version manifest at " + MANIFEST_URL + ": " + e.getMessage(), e);
        }
    }

    public VersionDetail fetchVersionDetail(VersionEntry entry) throws IOException {
        String body = fetcher.getString(entry.url());
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();

            String id = root.get("id").getAsString();
            String mainClass = root.get("mainClass").getAsString();

            JsonObject clientDownloadJson = root.getAsJsonObject("downloads").getAsJsonObject("client");
            VersionDetail.ClientDownload clientDownload = new VersionDetail.ClientDownload(
                clientDownloadJson.get("url").getAsString(),
                clientDownloadJson.get("sha1").getAsString(),
                clientDownloadJson.get("size").getAsLong()
            );

            List<VersionDetail.Library> libraries = new ArrayList<>();
            for (var libraryElement : root.getAsJsonArray("libraries")) {
                JsonObject libraryJson = libraryElement.getAsJsonObject();
                JsonObject downloads = libraryJson.getAsJsonObject("downloads");
                if (downloads == null || !downloads.has("artifact")) continue;
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                libraries.add(new VersionDetail.Library(
                    artifact.get("path").getAsString(),
                    artifact.get("url").getAsString(),
                    artifact.get("sha1").getAsString(),
                    artifact.get("size").getAsLong()
                ));
            }

            JsonObject assetIndexJson = root.getAsJsonObject("assetIndex");
            VersionDetail.AssetIndexRef assetIndex = new VersionDetail.AssetIndexRef(
                assetIndexJson.get("id").getAsString(),
                assetIndexJson.get("url").getAsString(),
                assetIndexJson.get("sha1").getAsString()
            );

            // Absent only in very old manifests; those versions predate 17 and run on 8.
        int javaMajorVersion = 8;
        if (root.has("javaVersion")) {
            javaMajorVersion = root.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        }

        return new VersionDetail(
            id, mainClass, clientDownload, libraries, assetIndex, javaMajorVersion);
        } catch (RuntimeException e) {
            throw new IOException("Malformed version detail at " + entry.url() + ": " + e.getMessage(), e);
        }
    }

    public VersionEntry findVersion(List<VersionEntry> versions, String mcVersion) {
        return versions.stream()
            .filter(v -> v.id().equals(mcVersion))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown Minecraft version: " + mcVersion));
    }
}
