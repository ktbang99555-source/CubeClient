package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.auth.Session;
import com.cubeclient.launcher.download.AssetDownloader;
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
            if (url.equals("https://example.com/17.json")) {
                return """
                    {
                      "objects": {
                        "minecraft/lang/en_us.json": {
                          "hash": "abcdef0123456789abcdef0123456789abcdef01", "size": 10
                        }
                      }
                    }
                    """;
            }
            return """
                {
                  "id": "1.21.4",
                  "mainClass": "net.minecraft.client.main.Main",
                  "downloads": { "client": { "url": "https://example.com/client.jar", "sha1": "IGNORED", "size": 1 } },
                  "libraries": [
                    {
                      "name": "com.example:foo:1.0",
                      "downloads": {
                        "artifact": {
                          "path": "com/example/foo/1.0/foo-1.0.jar",
                          "url": "https://example.com/foo-1.0.jar",
                          "sha1": "IGNORED",
                          "size": 2
                        }
                      }
                    }
                  ],
                  "assetIndex": { "id": "17", "url": "https://example.com/17.json", "sha1": "IGNORED" }
                }
                """;
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "fake-jar-bytes");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
            throw new UnsupportedOperationException("not used");
        }
    }

    /**
     * Records every destination it is asked to write to. The recorded paths are the only thing
     * that can catch a regression where downloads are resolved against {@code gameDir} instead of
     * {@code sharedRoot} — such a bug leaves the exit code, the assembled command, and every
     * emitted event completely unchanged, so nothing else in this test would notice it.
     */
    static class NoVerifyDownloader extends Downloader {
        final List<Path> destinations = new ArrayList<>();

        NoVerifyDownloader(HttpFetcher fetcher) { super(fetcher); }

        @Override
        public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
            destinations.add(destination);
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

        AssetDownloader assetDownloader = new AssetDownloader(fetcher, downloader);
        LaunchCommand launchCommand = new LaunchCommand(
            manifestFetcher, downloader, assetDownloader, new JvmArgsBuilder(), processRunner, events);

        Profile profile = new Profile("latest-1.21", "1.21.4", "vanilla", List.of());
        Path sharedRoot = tempDir;
        Path gameDir = sharedRoot.resolve("instances").resolve("latest-1.21");
        Path javaBin = sharedRoot.resolve("runtimes/17/bin/java");

        int exitCode = launchCommand.run(
            profile, gameDir, sharedRoot, javaBin, Session.offline(profile.id()));

        assertEquals(0, exitCode);
        assertTrue(processRunner.lastCommand.contains("net.minecraft.client.main.Main"));

        // Downloads must land under sharedRoot, NOT under gameDir. Assert the exact paths:
        // this is the only assertion that fails if the sharedRoot/gameDir wiring regresses.
        assertEquals(
            List.of(
                sharedRoot.resolve(Path.of("libraries", "com", "example", "foo", "1.0", "foo-1.0.jar")),
                sharedRoot.resolve(Path.of("versions", "1.21.4", "1.21.4.jar")),
                // Assets are what make the launch actually playable — sounds and language files.
                sharedRoot.resolve(Path.of(
                    "assets", "objects", "ab", "abcdef0123456789abcdef0123456789abcdef01"))
            ),
            downloader.destinations);

        // The classpath must point at the code artifacts. Assets are NOT classpath entries —
        // the game finds them through --assetsDir, so exclude the asset object here.
        String classpath = processRunner.lastCommand.get(processRunner.lastCommand.indexOf("-cp") + 1);
        for (Path destination : downloader.destinations.subList(0, 2)) {
            assertTrue(classpath.contains(destination.toString()),
                "classpath is missing downloaded file " + destination);
        }

        // The game reads the asset index off disk at startup; it must be saved, and --assetsDir
        // must point at the tree that holds it.
        assertTrue(Files.exists(sharedRoot.resolve(Path.of("assets", "indexes", "17.json"))));
        assertEquals(
            sharedRoot.resolve("assets").toString(),
            processRunner.lastCommand.get(processRunner.lastCommand.indexOf("--assetsDir") + 1));

        String eventLog = out.toString();
        assertTrue(eventLog.contains("\"stage\":\"manifest\""));
        assertTrue(eventLog.contains("\"stage\":\"libraries\""));
        assertTrue(eventLog.contains("\"stage\":\"client_jar\""));
        assertTrue(eventLog.contains("\"stage\":\"assets\""));
        assertTrue(eventLog.contains("\"type\":\"launched\""));
        assertTrue(eventLog.contains("\"type\":\"exited\""));
    }
}
