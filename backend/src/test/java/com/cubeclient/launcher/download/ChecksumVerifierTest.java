package com.cubeclient.launcher.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesSha1ReturnsTrueForCorrectHash() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        // sha1("hello world") = 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed
        assertTrue(ChecksumVerifier.matchesSha1(file, "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
    }

    @Test
    void matchesSha1ReturnsFalseForWrongHash() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        assertFalse(ChecksumVerifier.matchesSha1(file, "0000000000000000000000000000000000000000"));
    }

    // Mojang publishes SHA-1, but Adoptium — the source of the bundled JREs — publishes
    // SHA-256. Verifying an Adoptium download against SHA-1 can never succeed.
    @Test
    void matchesSha256ReturnsTrueForCorrectHash() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        // sha256("hello world")
        assertTrue(ChecksumVerifier.matchesSha256(
            file, "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"));
    }

    @Test
    void matchesSha256ReturnsFalseForWrongHash() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        assertFalse(ChecksumVerifier.matchesSha256(file, "0".repeat(64)));
    }

    // The algorithms must not be conflated: a SHA-1 digest of the same bytes must not
    // satisfy a SHA-256 check.
    @Test
    void sha1DigestDoesNotSatisfyASha256Check() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        assertFalse(ChecksumVerifier.matchesSha256(file, "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
    }
}
