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
}
