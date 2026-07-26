package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Downloader {
    private final HttpFetcher fetcher;

    public Downloader(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
        if (Files.exists(destination) && ChecksumVerifier.matchesSha1(destination, expectedSha1)) {
            return;
        }
        fetcher.downloadToFile(url, destination);
        if (!ChecksumVerifier.matchesSha1(destination, expectedSha1)) {
            throw new IOException("Checksum mismatch after downloading " + url);
        }
    }
}
