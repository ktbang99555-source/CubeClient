package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Downloads a file and proves it arrived intact, skipping the transfer when a valid copy is
 * already on disk.
 *
 * <p>Two variants exist because the upstreams publish different digests: Mojang uses SHA-1 for
 * libraries, client jars, and assets; Adoptium uses SHA-256 for JRE archives.
 */
public class Downloader {
    private final HttpFetcher fetcher;

    public Downloader(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** For Mojang-published files. */
    public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
        download(url, destination, expectedSha1, false);
    }

    /** For Adoptium-published JRE archives. */
    public void downloadVerifiedSha256(String url, Path destination, String expectedSha256)
            throws IOException {
        download(url, destination, expectedSha256, true);
    }

    private void download(String url, Path destination, String expected, boolean sha256)
            throws IOException {
        if (Files.exists(destination) && matches(destination, expected, sha256)) {
            return;
        }
        fetcher.downloadToFile(url, destination);
        if (!matches(destination, expected, sha256)) {
            throw new IOException("Checksum mismatch after downloading " + url);
        }
    }

    private boolean matches(Path file, String expected, boolean sha256) throws IOException {
        return sha256
            ? ChecksumVerifier.matchesSha256(file, expected)
            : ChecksumVerifier.matchesSha1(file, expected);
    }
}
