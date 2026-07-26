package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;
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

class DownloaderTest {

    @TempDir
    Path tempDir;

    static class RecordingFetcher implements HttpFetcher {
        final List<String> downloadedUrls = new ArrayList<>();
        final String contentToWrite;

        RecordingFetcher(String contentToWrite) { this.contentToWrite = contentToWrite; }

        @Override
        public String getString(String url) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            downloadedUrls.add(url);
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, contentToWrite);
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
            throw new UnsupportedOperationException("not used");
        }
    }

    @Test
    void downloadsFileAndVerifiesChecksum() throws IOException {
        // sha1("hello world") = 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed
        RecordingFetcher fetcher = new RecordingFetcher("hello world");
        Downloader downloader = new Downloader(fetcher);
        Path destination = tempDir.resolve("out.jar");

        downloader.downloadVerified("https://example.com/out.jar", destination,
            "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed");

        assertEquals(1, fetcher.downloadedUrls.size());
        assertTrue(Files.exists(destination));
    }

    @Test
    void skipsDownloadIfExistingFileAlreadyMatches() throws IOException {
        Path destination = tempDir.resolve("out.jar");
        Files.writeString(destination, "hello world");
        RecordingFetcher fetcher = new RecordingFetcher("hello world");
        Downloader downloader = new Downloader(fetcher);

        downloader.downloadVerified("https://example.com/out.jar", destination,
            "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed");

        assertEquals(0, fetcher.downloadedUrls.size());
    }

    @Test
    void throwsIfDownloadedFileFailsChecksum() {
        RecordingFetcher fetcher = new RecordingFetcher("wrong content");
        Downloader downloader = new Downloader(fetcher);
        Path destination = tempDir.resolve("out.jar");

        assertThrows(IOException.class, () -> downloader.downloadVerified(
            "https://example.com/out.jar", destination, "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
    }
}
