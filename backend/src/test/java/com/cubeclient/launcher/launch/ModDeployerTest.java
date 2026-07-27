package com.cubeclient.launcher.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDeployerTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesTheJarIntoTheGameDirsModsFolder() throws IOException {
        Path source = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(source, "jar-bytes");
        Path gameDir = tempDir.resolve("instance");

        new ModDeployer().deploy(source, gameDir);

        Path deployed = gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar");
        assertTrue(Files.exists(deployed));
        assertEquals("jar-bytes", Files.readString(deployed));
    }

    // A profile is relaunched far more often than the mod jar changes, and re-copying tens of
    // kilobytes on every launch is wasted work the checksum comparison avoids — same reasoning
    // the network downloader already applies to Mojang's assets.
    //
    // This does NOT prove the skip by comparing file modification times before/after, as a
    // first draft of this test did. A manual check on this machine showed NTFS timestamps are
    // not reliable enough for that: writing a file 10ms after reading its mtime sometimes
    // produced an *earlier* millisecond value than the original, so "timestamp unchanged" would
    // not actually distinguish "skipped" from "rewritten" — it would just as often be vacuously
    // true or spuriously false. Instead, the destination is made read-only. Files.copy(...,
    // REPLACE_EXISTING) cannot overwrite a read-only file on Windows without clearing the
    // attribute first, which ModDeployer does not do, so any attempted copy throws
    // AccessDeniedException. A passing test therefore proves deploy() never touched the file at
    // all, not just that some best-effort timestamp looked the same.
    @Test
    void skipsTheCopyWhenAnIdenticalJarIsAlreadyDeployed() throws IOException {
        Path source = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(source, "jar-bytes");
        Path gameDir = tempDir.resolve("instance");
        Path deployed = gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar");
        Files.createDirectories(deployed.getParent());
        Files.writeString(deployed, "jar-bytes");

        deployed.toFile().setReadOnly();
        try {
            new ModDeployer().deploy(source, gameDir);
            assertEquals("jar-bytes", Files.readString(deployed),
                "the identical file must still be there, untouched");
        } finally {
            // Otherwise @TempDir cleanup cannot delete a read-only file on Windows.
            deployed.toFile().setWritable(true);
        }
    }

    @Test
    void replacesAnOutdatedJarWithADifferentOne() throws IOException {
        Path source = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(source, "new-bytes");
        Path gameDir = tempDir.resolve("instance");
        Path deployed = gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar");
        Files.createDirectories(deployed.getParent());
        Files.writeString(deployed, "old-bytes");

        new ModDeployer().deploy(source, gameDir);

        assertEquals("new-bytes", Files.readString(deployed));
    }
}
