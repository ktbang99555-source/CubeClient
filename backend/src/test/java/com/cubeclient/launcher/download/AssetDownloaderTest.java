package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;
import com.cubeclient.launcher.manifest.VersionDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetDownloaderTest {

    @TempDir
    Path tempDir;

    /** Serves a canned asset index and records every download it is asked to perform. */
    static class FakeFetcher implements HttpFetcher {
        final String indexJson;
        FakeFetcher(String indexJson) { this.indexJson = indexJson; }

        @Override
        public String getString(String url) {
            return indexJson;
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "asset-bytes");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /** Records destinations and source URLs without doing real checksum verification. */
    static class RecordingDownloader extends Downloader {
        final List<String> urls = new ArrayList<>();
        final List<Path> destinations = new ArrayList<>();

        RecordingDownloader(HttpFetcher fetcher) { super(fetcher); }

        @Override
        public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
            urls.add(url);
            destinations.add(destination);
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "asset-bytes");
        }
    }

    private static final String INDEX_JSON = """
        {
          "objects": {
            "minecraft/lang/en_us.json": { "hash": "abcdef0123456789abcdef0123456789abcdef01", "size": 10 },
            "minecraft/sounds/step/grass1.ogg": { "hash": "1234567890abcdef1234567890abcdef12345678", "size": 20 }
          }
        }
        """;

    private VersionDetail.AssetIndexRef indexRef() {
        return new VersionDetail.AssetIndexRef("17", "https://example.com/17.json", "INDEXSHA1");
    }

    @Test
    void savesTheAssetIndexUnderAssetsIndexes() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(INDEX_JSON);
        RecordingDownloader downloader = new RecordingDownloader(fetcher);
        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);

        assetDownloader.downloadAssets(indexRef(), tempDir, (done, total) -> {});

        Path indexFile = tempDir.resolve(Path.of("assets", "indexes", "17.json"));
        assertTrue(Files.exists(indexFile), "asset index should be saved for the game to read");
    }

    @Test
    void downloadsEachObjectToHashShardedPath() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(INDEX_JSON);
        RecordingDownloader downloader = new RecordingDownloader(fetcher);
        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);

        assetDownloader.downloadAssets(indexRef(), tempDir, (done, total) -> {});

        Path objects = tempDir.resolve(Path.of("assets", "objects"));
        // Minecraft looks assets up at objects/<first two hex chars>/<full hash>.
        assertTrue(downloader.destinations.contains(
            objects.resolve(Path.of("ab", "abcdef0123456789abcdef0123456789abcdef01"))));
        assertTrue(downloader.destinations.contains(
            objects.resolve(Path.of("12", "1234567890abcdef1234567890abcdef12345678"))));
        assertEquals(2, downloader.destinations.size());
    }

    @Test
    void downloadsObjectsFromTheResourcesHost() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(INDEX_JSON);
        RecordingDownloader downloader = new RecordingDownloader(fetcher);
        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);

        assetDownloader.downloadAssets(indexRef(), tempDir, (done, total) -> {});

        assertTrue(downloader.urls.contains(
            "https://resources.download.minecraft.net/ab/abcdef0123456789abcdef0123456789abcdef01"));
        assertTrue(downloader.urls.contains(
            "https://resources.download.minecraft.net/12/1234567890abcdef1234567890abcdef12345678"));
    }

    @Test
    void reportsProgressAsObjectsComplete() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(INDEX_JSON);
        RecordingDownloader downloader = new RecordingDownloader(fetcher);
        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);

        List<String> ticks = new ArrayList<>();
        assetDownloader.downloadAssets(indexRef(), tempDir, (done, total) -> ticks.add(done + "/" + total));

        // A real index has thousands of objects; without progress the UI looks frozen.
        assertEquals(List.of("1/2", "2/2"), ticks);
    }

    @Test
    void malformedIndexSurfacesAsIOException() {
        FakeFetcher fetcher = new FakeFetcher("{ this is not json");
        RecordingDownloader downloader = new RecordingDownloader(fetcher);
        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);

        IOException thrown = assertThrows(IOException.class,
            () -> assetDownloader.downloadAssets(indexRef(), tempDir, (done, total) -> {}));
        assertTrue(thrown.getMessage().contains("https://example.com/17.json"));
    }

    @Test
    void anIndexWithNoObjectsIsNotAnError() throws IOException {
        FakeFetcher fetcher = new FakeFetcher("{ \"objects\": {} }");
        RecordingDownloader downloader = new RecordingDownloader(fetcher);
        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);

        assetDownloader.downloadAssets(indexRef(), tempDir, (done, total) -> {});

        assertEquals(0, downloader.destinations.size());
    }
}
