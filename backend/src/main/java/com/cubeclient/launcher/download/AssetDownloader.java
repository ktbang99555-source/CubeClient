package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Downloads a version's assets — sounds, language files, and other non-code resources.
 *
 * <p>Without these the game process still starts, but with no sound and no text, so this is
 * required for a genuinely playable launch rather than a nicety.
 *
 * <p>Assets are content-addressed: the index maps a friendly name to a SHA-1, and the game
 * looks the file up at {@code objects/<first two hex chars>/<full hash>}. The sharding is
 * Mojang's layout, not ours — the game will not find the files anywhere else.
 */
public class AssetDownloader {

    private static final String RESOURCES_HOST = "https://resources.download.minecraft.net";

    /** Reports completed/total so a multi-thousand-file download does not look frozen. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int completed, int total);
    }

    private final HttpFetcher fetcher;
    private final Downloader downloader;

    public AssetDownloader(HttpFetcher fetcher, Downloader downloader) {
        this.fetcher = fetcher;
        this.downloader = downloader;
    }

    /**
     * @param sharedRoot {@code %APPDATA%/CubeClient} — assets live under {@code sharedRoot/assets},
     *                   shared by every profile, matching what {@code --assetsDir} is pointed at.
     */
    public void downloadAssets(VersionDetail.AssetIndexRef assetIndex, Path sharedRoot,
                               ProgressListener listener) throws IOException {
        String indexJson = fetcher.getString(assetIndex.url());

        // The game reads this file itself at startup, so it must be on disk under the id it
        // was published as, not just parsed in memory.
        Path indexFile = sharedRoot.resolve(Path.of("assets", "indexes", assetIndex.id() + ".json"));
        Files.createDirectories(indexFile.getParent());
        Files.writeString(indexFile, indexJson);

        JsonObject objects;
        try {
            objects = JsonParser.parseString(indexJson).getAsJsonObject().getAsJsonObject("objects");
        } catch (RuntimeException e) {
            throw new IOException(
                "Malformed asset index at " + assetIndex.url() + ": " + e.getMessage(), e);
        }
        if (objects == null) {
            throw new IOException("Asset index at " + assetIndex.url() + " has no \"objects\"");
        }

        Path objectsDir = sharedRoot.resolve(Path.of("assets", "objects"));
        int total = objects.size();
        int completed = 0;

        for (Map.Entry<String, com.google.gson.JsonElement> entry : objects.entrySet()) {
            String hash;
            try {
                hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
            } catch (RuntimeException e) {
                throw new IOException(
                    "Asset \"" + entry.getKey() + "\" in " + assetIndex.url() + " has no usable hash", e);
            }
            String shard = hash.substring(0, 2);
            downloader.downloadVerified(
                RESOURCES_HOST + "/" + shard + "/" + hash,
                objectsDir.resolve(shard).resolve(hash),
                hash
            );
            listener.onProgress(++completed, total);
        }
    }
}
