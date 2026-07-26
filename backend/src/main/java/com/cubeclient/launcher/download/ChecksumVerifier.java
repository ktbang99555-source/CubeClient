package com.cubeclient.launcher.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChecksumVerifier {
    private ChecksumVerifier() {}

    public static boolean matchesSha1(Path file, String expectedSha1) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            String actual = HexFormat.of().formatHex(hash);
            return actual.equalsIgnoreCase(expectedSha1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
