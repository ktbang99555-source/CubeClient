package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.download.ChecksumVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Puts the CubeClient mod jar into a profile's mods/ folder, the local-file counterpart to how
 * {@link com.cubeclient.launcher.loader.LoaderInstaller} fetches loader libraries over the
 * network — same "skip if the checksum already matches" idea, applied to a copy instead of a
 * download.
 */
public class ModDeployer {

    public void deploy(Path sourceJar, Path gameDir) throws IOException {
        Path destination = gameDir.resolve("mods").resolve(sourceJar.getFileName());

        if (Files.exists(destination) && ChecksumVerifier.matchesSha256(destination, sha256Of(sourceJar))) {
            return;
        }

        Files.createDirectories(destination.getParent());
        Files.copy(sourceJar, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private String sha256Of(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
