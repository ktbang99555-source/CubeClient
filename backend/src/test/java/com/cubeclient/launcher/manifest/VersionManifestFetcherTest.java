package com.cubeclient.launcher.manifest;

import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionManifestFetcherTest {

    static class FakeHttpFetcher implements HttpFetcher {
        @Override
        public HttpFetcher.HttpResult postJsonAllowingErrors(
                String url, String jsonBody, java.util.Map<String, String> headers) throws java.io.IOException {
            return new HttpFetcher.HttpResult(200, postJson(url, jsonBody, headers));
        }

        @Override
        public HttpFetcher.HttpResult postForm(String url, java.util.Map<String, String> form) {
            throw new UnsupportedOperationException("not used");
        }

        private final Map<String, String> responses;
        FakeHttpFetcher(Map<String, String> responses) { this.responses = responses; }

        @Override
        public String getString(String url) {
            String body = responses.get(url);
            if (body == null) throw new IllegalStateException("No fake response for " + url);
            return body;
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
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

    @Test
    void fetchVersionDetailSkipsLibrariesThatCannotBeDownloadedAsArtifact() throws IOException {
        String detailUrl = "https://example.com/1.8.9.json";
        String detailJson = """
            {
              "id": "1.8.9",
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
                },
                {
                  "name": "net.java.jinput:jinput-platform:2.0.5",
                  "downloads": {
                    "classifiers": {
                      "natives-windows": {
                        "path": "net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-windows.jar",
                        "url": "https://example.com/jinput-natives-windows.jar",
                        "sha1": "aaa111",
                        "size": 10
                      }
                    }
                  }
                },
                {
                  "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209",
                  "rules": [ { "action": "allow", "os": { "name": "windows" } } ]
                }
              ],
              "assetIndex": { "id": "17", "url": "https://example.com/17.json", "sha1": "ghi789" }
            }
            """;
        FakeHttpFetcher fetcher = new FakeHttpFetcher(Map.of(detailUrl, detailJson));
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);

        VersionDetail detail = manifestFetcher.fetchVersionDetail(new VersionEntry("1.8.9", detailUrl));

        assertEquals(1, detail.libraries().size());
        assertEquals("com/example/foo/1.0/foo-1.0.jar", detail.libraries().get(0).relativePath());
    }

    @Test
    void findVersionReturnsMatchingEntry() {
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(new FakeHttpFetcher(Map.of()));
        List<VersionEntry> versions = List.of(
            new VersionEntry("1.21.4", "https://example.com/1.21.4.json"),
            new VersionEntry("1.8.9", "https://example.com/1.8.9.json")
        );

        VersionEntry found = manifestFetcher.findVersion(versions, "1.8.9");

        assertEquals(new VersionEntry("1.8.9", "https://example.com/1.8.9.json"), found);
    }

    @Test
    void findVersionThrowsWhenVersionNotFound() {
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(new FakeHttpFetcher(Map.of()));
        List<VersionEntry> versions = List.of(
            new VersionEntry("1.21.4", "https://example.com/1.21.4.json")
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> manifestFetcher.findVersion(versions, "1.16.5"));
        assertTrue(exception.getMessage().contains("1.16.5"));
    }
}
