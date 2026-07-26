package com.cubeclient.launcher.download;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies downloaded files against a published digest.
 *
 * <p>Two algorithms are needed because the two upstreams disagree: Mojang publishes SHA-1 for
 * libraries, client jars, and assets, while Adoptium publishes SHA-256 for JRE archives.
 * Checking an Adoptium archive with SHA-1 can never match.
 */
public final class ChecksumVerifier {
    private ChecksumVerifier() {}

    public static boolean matchesSha1(Path file, String expectedSha1) throws IOException {
        return matches(file, "SHA-1", expectedSha1);
    }

    public static boolean matchesSha256(Path file, String expectedSha256) throws IOException {
        return matches(file, "SHA-256", expectedSha256);
    }

    private static boolean matches(Path file, String algorithm, String expected) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        }
        // Streamed rather than readAllBytes: JRE archives are tens of megabytes and there is no
        // reason to hold one entirely in memory just to hash it.
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestStream = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestStream.read(buffer) != -1) {
                // reading is what updates the digest
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
    }
}
