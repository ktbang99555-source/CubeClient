package com.cubeclient.launcher.manifest;

import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionManifestFetcherTest {

    static class FakeHttpFetcher implements HttpFetcher {
        private final Map<String, String> responses;
        FakeHttpFetcher(Map<String, String> responses) { this.responses = responses; }

        @Override
        public String getString(String url) {
            String body = responses.get(url);
            if (body == null) throw new IllegalStateException("No fake response for " + url);
            return body;
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            throw new UnsupportedOperationException("not used in this test");
        }
    }

    private static final String MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    @Test
    void fetchVersionListParsesEntries() throws IOException {
        String manifestJson = """
            {
              "latest": { "release": "1.21.4", "snapshot": "1.21.4" },
              "versions": [
                { "id": "1.21.4", "type": "release", "url": "https://example.com/1.21.4.json" },
                { "id": "1.8.9", "type": "release", "url": "https://example.com/1.8.9.json" }
              ]
            }
            """;
        FakeHttpFetcher fetcher = new FakeHttpFetcher(Map.of(MANIFEST_URL, manifestJson));
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);

        List<VersionEntry> versions = manifestFetcher.fetchVersionList();

        assertEquals(2, versions.size());
        assertEquals(new VersionEntry("1.21.4", "https://example.com/1.21.4.json"), versions.get(0));
        assertEquals(new VersionEntry("1.8.9", "https://example.com/1.8.9.json"), versions.get(1));
    }

    @Test
    void fetchVersionDetailParsesLibrariesAndClientDownload() throws IOException {
        String detailUrl = "https://example.com/1.21.4.json";
        String detailJson = """
            {
              "id": "1.21.4",
              "mainClass": "net.minecraft.client.main.Main",
              "downloads": {
                "client": { "url": "https://example.com/client.jar", "sha1": "abc123", "size": 100 }
              },
              "libraries": [
                {
                  "name": "com.example:foo:1.0",
                  "downloads": {
                    "artifact": {
                      "path": "com/example/foo/1.0/foo-1.0.jar",
                      "url": "https://example.com/foo-1.0.jar",
                      "sha1": "def456",
                      "size": 50
                    }
                  }
                }
              ],
              "assetIndex": { "id": "17", "url": "https://example.com/17.json", "sha1": "ghi789" }
            }
            """;
        FakeHttpFetcher fetcher = new FakeHttpFetcher(Map.of(detailUrl, detailJson));
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);

        VersionDetail detail = manifestFetcher.fetchVersionDetail(new VersionEntry("1.21.4", detailUrl));

        assertEquals("1.21.4", detail.id());
        assertEquals("net.minecraft.client.main.Main", detail.mainClass());
        assertEquals("https://example.com/client.jar", detail.clientDownload().url());
        assertEquals("abc123", detail.clientDownload().sha1());
        assertEquals(1, detail.libraries().size());
        assertEquals("com/example/foo/1.0/foo-1.0.jar", detail.libraries().get(0).relativePath());
        assertEquals("17", detail.assetIndex().id());
    }
}
