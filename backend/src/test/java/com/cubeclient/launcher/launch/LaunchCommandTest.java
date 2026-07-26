package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.events.EventEmitter;
import com.cubeclient.launcher.http.HttpFetcher;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.manifest.VersionEntry;
import com.cubeclient.launcher.manifest.VersionManifestFetcher;
import com.cubeclient.launcher.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchCommandTest {

    @TempDir
    Path tempDir;

    static class FakeHttpFetcher implements HttpFetcher {
        @Override
        public String getString(String url) {
            String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
            if (url.equals(manifestUrl)) {
                return """
                    { "latest": {"release":"1.21.4","snapshot":"1.21.4"},
                      "versions": [ { "id": "1.21.4", "type": "release", "url": "https://example.com/1.21.4.json" } ] }
                    """;
            }
            return """
                {
                  "id": "1.21.4",
                  "mainClass": "net.minecraft.client.main.Main",
                  "downloads": { "client": { "url": "https://example.com/client.jar", "sha1": "IGNORED", "size": 1 } },
                  "libraries": [],
                  "assetIndex": { "id": "17", "url": "https://example.com/17.json", "sha1": "IGNORED" }
                }
                """;
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "fake-jar-bytes");
        }
    }

    static class NoVerifyDownloader extends Downloader {
        NoVerifyDownloader(HttpFetcher fetcher) { super(fetcher); }

        @Override
        public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
            if (!Files.exists(destination)) {
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, "fake-jar-bytes");
            }
        }
    }

    static class FakeProcessRunner implements ProcessRunner {
        List<String> lastCommand;

        @Override
        public Process start(List<String> command, Path workingDir) throws IOException {
            this.lastCommand = new ArrayList<>(command);
            // Start a trivial real process so Process#waitFor works cross-platform in the test.
            ProcessBuilder builder = new ProcessBuilder(
                System.getProperty("os.name").toLowerCase().contains("win")
                    ? List.of("cmd", "/c", "exit 0")
                    : List.of("true")
            );
            return builder.start();
        }
    }

    @Test
    void runDownloadsFilesBuildsCommandAndLaunchesProcess() throws IOException {
        FakeHttpFetcher fetcher = new FakeHttpFetcher();
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);
        NoVerifyDownloader downloader = new NoVerifyDownloader(fetcher);
        FakeProcessRunner processRunner = new FakeProcessRunner();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter events = new EventEmitter(new PrintStream(out));

        LaunchCommand launchCommand = new LaunchCommand(
            manifestFetcher, downloader, new JvmArgsBuilder(), processRunner, events);

        Profile profile = new Profile("latest-1.21", "1.21.4", "vanilla", List.of());
        Path sharedRoot = tempDir;
        Path gameDir = sharedRoot.resolve("instances").resolve("latest-1.21");
        Path javaBin = sharedRoot.resolve("runtimes/17/bin/java");

        int exitCode = launchCommand.run(profile, gameDir, sharedRoot, javaBin);

        assertEquals(0, exitCode);
        assertTrue(processRunner.lastCommand.contains("net.minecraft.client.main.Main"));
        String eventLog = out.toString();
        assertTrue(eventLog.contains("\"stage\":\"manifest\""));
        assertTrue(eventLog.contains("\"stage\":\"client_jar\""));
        assertTrue(eventLog.contains("\"type\":\"launched\""));
        assertTrue(eventLog.contains("\"type\":\"exited\""));
    }
}
