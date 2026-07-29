package com.cubeclient.launcher.loader;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fabric libraries are Maven coordinates rather than the direct, pre-hashed download entries
 * Mojang publishes, so each coordinate has to be translated into a repository path and URL.
 */
class LoaderInstallerTest {

    @TempDir
    Path tempDir;

    private static final String LOADER_LIST = """
        [ { "loader": { "version": "0.19.3", "stable": true } },
          { "loader": { "version": "0.18.0", "stable": true } } ]
        """;

    private static final String PROFILE = """
        {
          "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
          "inheritsFrom": "1.21.4",
          "libraries": [
            { "name": "org.ow2.asm:asm:9.10.1", "url": "https://maven.fabricmc.net/" },
            { "name": "net.fabricmc:fabric-loader:0.19.3", "url": "https://maven.fabricmc.net/" }
          ]
        }
        """;

    static class FakeFetcher implements HttpFetcher {
        final List<String> requested = new ArrayList<>();

        @Override
        public String getString(String url) {
            requested.add(url);
            if (url.endsWith(".sha1")) {
                return "  ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236  \n";
            }
            if (url.contains("/profile/json")) {
                return PROFILE;
            }
            return LOADER_LIST;
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "jar-bytes");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public HttpResult postForm(String url, Map<String, String> form) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public HttpResult postJsonAllowingErrors(String url, String body, Map<String, String> h) {
            throw new UnsupportedOperationException("not used");
        }
    }

    static class RecordingDownloader extends Downloader {
        final List<String> urls = new ArrayList<>();
        final List<Path> destinations = new ArrayList<>();
        final List<String> expectedHashes = new ArrayList<>();

        RecordingDownloader(HttpFetcher fetcher) { super(fetcher); }

        @Override
        public void downloadVerified(String url, Path destination, String expectedSha1)
                throws IOException {
            urls.add(url);
            destinations.add(destination);
            expectedHashes.add(expectedSha1);
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "jar-bytes");
        }
    }

    private LoaderInstaller installer(FakeFetcher fetcher, RecordingDownloader downloader) {
        return new LoaderInstaller(fetcher, downloader);
    }

    @Test
    void vanillaNeedsNoLoaderAndChangesNothing() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("vanilla", "1.21.4", tempDir);

        assertTrue(result.isEmpty());
        assertEquals(List.of(), result.extraClasspath());
        assertEquals(0, fetcher.requested.size(), "vanilla must not contact a loader host");
    }

    @Test
    void fabricOverridesTheMainClass() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        // Fabric boots through its own launcher, which then loads Minecraft. Leaving the vanilla
        // main class in place would start the game with no mods loaded at all.
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", result.mainClass());
    }

    @Test
    void translatesMavenCoordinatesIntoRepositoryPaths() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        // group.id:artifact:version  ->  group/id/artifact/version/artifact-version.jar
        assertTrue(downloader.urls.contains(
            "https://maven.fabricmc.net/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar"),
            "actual: " + downloader.urls);
        assertTrue(downloader.destinations.contains(
            tempDir.resolve(Path.of("libraries", "org", "ow2", "asm", "asm", "9.10.1", "asm-9.10.1.jar"))),
            "actual: " + downloader.destinations);
    }

    @Test
    void putsEveryDownloadedLoaderLibraryOnTheClasspath() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        // Only the 2 loader libraries from PROFILE. Fabric API is downloaded too, but it is a
        // mod rather than a library and is reported separately — see the tests below.
        assertEquals(2, result.extraClasspath().size());
    }

    // Fabric API is a separate distribution from Fabric Loader, but ScreenEvents and
    // HudRenderCallback — everything the CubeClient mod needs to add a settings button and draw
    // a HUD — live in Fabric API, not the loader. Every Fabric profile needs it, unconditionally.
    @Test
    void alsoInstallsFabricApiForEveryFabricProfile() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        assertTrue(downloader.urls.stream().anyMatch(u -> u.contains("fabric-api")),
            "actual: " + downloader.urls);
        assertTrue(result.modJars().stream().anyMatch(p -> p.toString().contains("fabric-api")),
            "actual: " + result.modJars());
    }

    /*
     * Caught by live testing, not by any test that existed at the time: Fabric API was put on
     * the classpath, and Fabric Loader refused to start with "requires fabric-api, which is
     * missing" — it discovers mods by scanning the profile's mods/ folder and never looks at
     * the classpath. The jar was present and invisible.
     *
     * It must not be in both places either. Loading one jar through two routes is how the
     * duplicate-ASM failure this launcher already hit happens.
     */
    @Test
    void fabricApiIsReportedAsAModAndNotAlsoAsALibrary() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        assertTrue(result.extraClasspath().stream()
                .noneMatch(p -> p.toString().contains("fabric-api")),
            "Fabric API must not be on the classpath: " + result.extraClasspath());
    }

    @Test
    void vanillaReportsNoModJars() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("vanilla", "1.21.4", tempDir);

        assertTrue(result.modJars().isEmpty());
    }

    @Test
    void vanillaDoesNotInstallFabricApiEither() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        installer(fetcher, downloader).install("vanilla", "1.21.4", tempDir);

        assertTrue(downloader.urls.isEmpty());
    }

    // Fabric's profile JSON carries no digests, so they come from the Maven repo's own .sha1
    // sibling. Skipping verification here would leave loader jars unchecked while Mojang's are
    // verified — an inconsistent and weaker guarantee than the project already promises.
    @Test
    void verifiesLoaderJarsUsingTheMavenSha1Sibling() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        assertTrue(fetcher.requested.stream().anyMatch(u -> u.endsWith("asm-9.10.1.jar.sha1")),
            "should fetch the .sha1 sibling; actual: " + fetcher.requested);
        // Whitespace around the published hash must be trimmed or verification always fails.
        assertTrue(downloader.expectedHashes.contains("ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236"),
            "actual: " + downloader.expectedHashes);
    }


    @Test
    void picksTheNewestLoaderVersionOffered() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        assertTrue(fetcher.requested.stream().anyMatch(u -> u.contains("/0.19.3/profile/json")),
            "should use the first (newest) entry; actual: " + fetcher.requested);
    }

    @Test
    void reportsWhichVanillaModulesItSupersedes() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        // The vanilla ASM must be removed from the classpath or Fabric refuses to start.
        assertTrue(result.supersededModules().contains("org/ow2/asm/asm"),
            "actual: " + result.supersededModules());
        assertTrue(result.supersededModules().contains("net/fabricmc/fabric-loader"),
            "actual: " + result.supersededModules());
    }

    @Test
    void moduleKeyStripsVersionAndFileName() {
        assertEquals("org/ow2/asm/asm",
            LoaderInstaller.moduleKey("org/ow2/asm/asm/9.6/asm-9.6.jar"));
        assertEquals("org/ow2/asm/asm",
            LoaderInstaller.moduleKey("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar"));
    }

    @Test
    void anUnknownLoaderIsRejectedRatherThanSilentlyIgnored() {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        IOException thrown = assertThrows(IOException.class,
            () -> installer(fetcher, downloader).install("forge", "1.21.4", tempDir));
        assertTrue(thrown.getMessage().contains("forge"), thrown.getMessage());
    }

    // Dropped along with 1.8.9: those versions publish natives as classifier artifacts that must
    // be unpacked and pointed at with -Djava.library.path, which this launcher does not do.
    @Test
    void legacyFabricIsNoLongerSupported() {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        IOException thrown = assertThrows(IOException.class,
            () -> installer(fetcher, downloader).install("legacyfabric", "1.8.9", tempDir));
        assertTrue(thrown.getMessage().contains("legacyfabric"), thrown.getMessage());
    }

    @Test
    void aVersionWithNoLoaderBuildSurfacesAsIOException() {
        FakeFetcher fetcher = new FakeFetcher() {
            @Override
            public String getString(String url) {
                requested.add(url);
                return "[]";
            }
        };
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        IOException thrown = assertThrows(IOException.class,
            () -> installer(fetcher, downloader).install("fabric", "1.99.0", tempDir));
        assertTrue(thrown.getMessage().contains("1.99.0"), thrown.getMessage());
    }
}
