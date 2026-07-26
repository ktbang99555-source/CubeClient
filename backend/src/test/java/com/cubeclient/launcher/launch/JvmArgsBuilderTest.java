package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.auth.Session;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.profile.Profile;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmArgsBuilderTest {

    @Test
    void buildProducesJavaCommandWithClasspathAndMainClass() {
        VersionDetail detail = new VersionDetail(
            "1.21.4",
            "net.minecraft.client.main.Main",
            new VersionDetail.ClientDownload("https://example.com/client.jar", "abc", 100),
            List.of(new VersionDetail.Library("com/example/foo/1.0/foo-1.0.jar", "https://example.com/foo.jar", "def", 50)),
            new VersionDetail.AssetIndexRef("17", "https://example.com/17.json", "ghi"),
            21
        );
        Profile profile = new Profile("latest-1.21", "1.21.4", "vanilla", List.of());
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        Path gameDir = sharedRoot.resolve(Path.of("instances", "latest-1.21"));
        Path javaBin = sharedRoot.resolve(Path.of("runtimes", "17", "bin", "java.exe"));

        List<String> command = new JvmArgsBuilder()
            .build(profile, detail, gameDir, sharedRoot, javaBin, Session.offline(profile.id()));

        assertEquals(javaBin.toString(), command.get(0));
        assertEquals("-cp", command.get(1));
        String classpath = command.get(2);
        List<String> classpathEntries = List.of(classpath.split(java.util.regex.Pattern.quote(File.pathSeparator)));
        assertEquals(2, classpathEntries.size());
        // Shared trees must sit directly under sharedRoot, NOT nested inside instances/.
        assertEquals(
            sharedRoot.resolve(Path.of("libraries", "com", "example", "foo", "1.0", "foo-1.0.jar")).toString(),
            classpathEntries.get(0));
        assertEquals(
            sharedRoot.resolve(Path.of("versions", "1.21.4", "1.21.4.jar")).toString(),
            classpathEntries.get(1));
        assertEquals("net.minecraft.client.main.Main", command.get(3));
        assertTrue(command.contains("--version"));
        assertTrue(command.contains("1.21.4"));

        // --gameDir is the isolated per-profile dir; --assetsDir is the shared tree.
        assertEquals(gameDir.toString(), command.get(command.indexOf("--gameDir") + 1));
        assertEquals(
            sharedRoot.resolve("assets").toString(),
            command.get(command.indexOf("--assetsDir") + 1));
        // The index id must match the file AssetDownloader saved under assets/indexes/.
        assertEquals("17", command.get(command.indexOf("--assetIndex") + 1));
    }

    private static VersionDetail sampleDetail() {
        return new VersionDetail(
            "1.21.4",
            "net.minecraft.client.main.Main",
            new VersionDetail.ClientDownload("https://example.com/client.jar", "abc", 100),
            List.of(new VersionDetail.Library(
                "com/example/foo/1.0/foo-1.0.jar", "https://example.com/foo.jar", "def", 50)),
            new VersionDetail.AssetIndexRef("17", "https://example.com/17.json", "ghi"),
            21
        );
    }

    // Without these the game runs as an unauthenticated placeholder and every server,
    // Hypixel included, rejects it.
    @Test
    void passesTheSessionIdentityToTheGame() {
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        List<String> command = new JvmArgsBuilder().build(
            new Profile("latest-1.21", "1.21.4", "vanilla", List.of()),
            sampleDetail(),
            sharedRoot.resolve(Path.of("instances", "latest-1.21")),
            sharedRoot,
            sharedRoot.resolve(Path.of("runtimes", "17", "bin", "java.exe")),
            Session.online("Steve", "abc123uuid", "MC_TOKEN"));

        assertEquals("Steve", command.get(command.indexOf("--username") + 1));
        assertEquals("abc123uuid", command.get(command.indexOf("--uuid") + 1));
        assertEquals("MC_TOKEN", command.get(command.indexOf("--accessToken") + 1));
        assertEquals("msa", command.get(command.indexOf("--userType") + 1));
    }

    @Test
    void anOfflineSessionIsNotLabelledAsAMicrosoftAccount() {
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        List<String> command = new JvmArgsBuilder().build(
            new Profile("latest-1.21", "1.21.4", "vanilla", List.of()),
            sampleDetail(),
            sharedRoot.resolve(Path.of("instances", "latest-1.21")),
            sharedRoot,
            sharedRoot.resolve(Path.of("runtimes", "17", "bin", "java.exe")),
            Session.offline("manual-test"));

        assertEquals("manual-test", command.get(command.indexOf("--username") + 1));
        assertEquals("legacy", command.get(command.indexOf("--userType") + 1));
    }
}
