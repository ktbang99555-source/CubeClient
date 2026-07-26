package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Enough to hide per-request latency without hammering Mojang's CDN. */
    private static final int DOWNLOAD_THREADS = 16;

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

        // Read every hash up front so a malformed entry fails before any download starts.
        List<String> hashes = new ArrayList<>(objects.size());
        for (Map.Entry<String, com.google.gson.JsonElement> entry : objects.entrySet()) {
            try {
                hashes.add(entry.getValue().getAsJsonObject().get("hash").getAsString());
            } catch (RuntimeException e) {
                throw new IOException(
                    "Asset \"" + entry.getKey() + "\" in " + assetIndex.url() + " has no usable hash", e);
            }
        }

        downloadObjects(hashes, objectsDir, listener);
    }

    /**
     * Fetches the objects concurrently.
     *
     * <p>A modern version's index lists several thousand small files. Downloading them one at a
     * time is latency-bound, not bandwidth-bound — measured at roughly 1.5 files/second, which
     * puts a first launch near 40 minutes. These are independent GETs to a CDN, so a modest pool
     * turns that into a couple of minutes.
     */
    private void downloadObjects(List<String> hashes, Path objectsDir, ProgressListener listener)
            throws IOException {
        int total = hashes.size();
        if (total == 0) {
            return;
        }

        AtomicInteger completed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(
            Math.min(DOWNLOAD_THREADS, total),
            runnable -> {
                Thread thread = new Thread(runnable, "asset-download");
                // Daemon so a stuck download can never keep the launcher process alive.
                thread.setDaemon(true);
                return thread;
            });

        try {
            List<Future<?>> pending = new ArrayList<>(total);
            for (String hash : hashes) {
                pending.add(pool.submit(() -> {
                    String shard = hash.substring(0, 2);
                    downloader.downloadVerified(
                        RESOURCES_HOST + "/" + shard + "/" + hash,
                        objectsDir.resolve(shard).resolve(hash),
                        hash
                    );
                    listener.onProgress(completed.incrementAndGet(), total);
                    return null;
                }));
            }

            for (Future<?> future : pending) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException io) {
                        throw io;
                    }
                    throw new IOException("Asset download failed", cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while downloading assets", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
