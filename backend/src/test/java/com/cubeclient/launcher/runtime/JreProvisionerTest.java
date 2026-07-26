package com.cubeclient.launcher.runtime;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JreProvisionerTest {

    @TempDir
    Path tempDir;

    private static final String ADOPTIUM_RESPONSE = """
        [
          {
            "binary": {
              "package": {
                "link": "https://example.com/jre17.zip",
                "checksum": "DEADBEEF",
                "name": "OpenJDK17U-jre_x64_windows_hotspot.zip"
              }
            }
          }
        ]
        """;

    /** Serves the Adoptium listing and writes a real zip shaped like an Adoptium JRE archive. */
    static class FakeFetcher implements HttpFetcher {
        final List<String> requestedUrls = new ArrayList<>();
        final String listing;

        FakeFetcher(String listing) { this.listing = listing; }

        @Override
        public String getString(String url) {
            requestedUrls.add(url);
            return listing;
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            writeJreZip(destination);
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }
    }

    /** Adoptium archives nest everything under a versioned top-level directory. */
    private static void writeJreZip(Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try (OutputStream out = Files.newOutputStream(destination);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("jdk-17.0.19+10-jre/bin/java.exe"));
            zip.write("fake-java-binary".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("jdk-17.0.19+10-jre/lib/modules"));
            zip.write("fake-modules".getBytes());
            zip.closeEntry();
        }
    }

    static class NoVerifyDownloader extends Downloader {
        NoVerifyDownloader(HttpFetcher fetcher) { super(fetcher); }

        @Override
        public void downloadVerifiedSha256(String url, Path destination, String expectedSha256)
                throws IOException {
            writeJreZip(destination);
        }
    }

    @Test
    void downloadsAndExtractsAJreThenReturnsItsJavaBinary() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(ADOPTIUM_RESPONSE);
        JreProvisioner provisioner = new JreProvisioner(fetcher, new NoVerifyDownloader(fetcher));

        Path javaBin = provisioner.ensureJre(17, tempDir, "windows");

        assertTrue(Files.exists(javaBin), "returned java binary should exist on disk: " + javaBin);
        assertTrue(javaBin.toString().replace('\\', '/').contains("/bin/java"),
            "should point at the bin/java executable, got " + javaBin);
        assertEquals("fake-java-binary", Files.readString(javaBin));
    }

    @Test
    void asksAdoptiumForTheRequestedMajorVersionAndAJreImage() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(ADOPTIUM_RESPONSE);
        JreProvisioner provisioner = new JreProvisioner(fetcher, new NoVerifyDownloader(fetcher));

        provisioner.ensureJre(8, tempDir, "windows");

        String requested = fetcher.requestedUrls.get(0);
        // 1.8.9 profiles need Java 8 while modern versions need 17 — asking for the wrong one
        // produces a JRE that cannot run the game at all.
        assertTrue(requested.contains("/8/"), "should request major version 8: " + requested);
        assertTrue(requested.contains("image_type=jre"), requested);
        assertTrue(requested.contains("os=windows"), requested);
    }

    @Test
    void reusesAnAlreadyProvisionedJreWithoutDownloading() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(ADOPTIUM_RESPONSE);
        JreProvisioner provisioner = new JreProvisioner(fetcher, new NoVerifyDownloader(fetcher));

        Path first = provisioner.ensureJre(17, tempDir, "windows");
        int callsAfterFirst = fetcher.requestedUrls.size();
        Path second = provisioner.ensureJre(17, tempDir, "windows");

        assertEquals(first, second);
        // A ~40MB re-download on every launch would be unacceptable.
        assertEquals(callsAfterFirst, fetcher.requestedUrls.size());
    }

    // Adoptium publishes SHA-256, not the SHA-1 that Mojang uses. Verifying the JRE archive
    // with the wrong algorithm can never succeed, so provisioning would always fail.
    @Test
    void verifiesTheJreArchiveWithSha256() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(ADOPTIUM_RESPONSE);
        List<String> algorithmsUsed = new ArrayList<>();
        Downloader recording = new Downloader(fetcher) {
            @Override
            public void downloadVerifiedSha256(String url, Path destination, String expectedSha256)
                    throws IOException {
                algorithmsUsed.add("sha256");
                writeJreZip(destination);
            }

            @Override
            public void downloadVerified(String url, Path destination, String expectedSha1)
                    throws IOException {
                algorithmsUsed.add("sha1");
                writeJreZip(destination);
            }
        };
        JreProvisioner provisioner = new JreProvisioner(fetcher, recording);

        provisioner.ensureJre(17, tempDir, "windows");

        assertEquals(List.of("sha256"), algorithmsUsed);
    }

    @Test
    void anEmptyAdoptiumListingSurfacesAsIOException() {
        FakeFetcher fetcher = new FakeFetcher("[]");
        JreProvisioner provisioner = new JreProvisioner(fetcher, new NoVerifyDownloader(fetcher));

        IOException thrown = assertThrows(IOException.class,
            () -> provisioner.ensureJre(17, tempDir, "windows"));
        assertTrue(thrown.getMessage().contains("17"), thrown.getMessage());
    }

    @Test
    void malformedAdoptiumListingSurfacesAsIOException() {
        FakeFetcher fetcher = new FakeFetcher("{ this is not json");
        JreProvisioner provisioner = new JreProvisioner(fetcher, new NoVerifyDownloader(fetcher));

        assertThrows(IOException.class, () -> provisioner.ensureJre(17, tempDir, "windows"));
    }

    // A zip entry named "../../evil" would otherwise be written outside the target directory.
    @Test
    void rejectsArchiveEntriesThatEscapeTheTargetDirectory() throws IOException {
        FakeFetcher fetcher = new FakeFetcher(ADOPTIUM_RESPONSE);
        Downloader evilDownloader = new Downloader(fetcher) {
            @Override
            public void downloadVerifiedSha256(String url, Path destination, String expectedSha256)
                    throws IOException {
                Files.createDirectories(destination.getParent());
                try (OutputStream out = Files.newOutputStream(destination);
                     ZipOutputStream zip = new ZipOutputStream(out)) {
                    zip.putNextEntry(new ZipEntry("../../escaped.txt"));
                    zip.write("pwned".getBytes());
                    zip.closeEntry();
                }
            }
        };
        JreProvisioner provisioner = new JreProvisioner(fetcher, evilDownloader);

        assertThrows(IOException.class, () -> provisioner.ensureJre(17, tempDir, "windows"));
        assertTrue(Files.notExists(tempDir.getParent().resolve("escaped.txt")),
            "archive entry must not be written outside the runtimes directory");
    }
}
