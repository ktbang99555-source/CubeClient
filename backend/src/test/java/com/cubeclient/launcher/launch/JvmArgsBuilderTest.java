package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.auth.Session;
import com.cubeclient.launcher.loader.LoaderInstaller.InstalledLoader;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.profile.Profile;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            .build(profile, detail, gameDir, sharedRoot, javaBin, Session.offline(profile.id()), InstalledLoader.none());

        assertEquals(javaBin.toString(), command.get(0));
        // The mod-config system property is a JVM arg and must precede -cp like any other.
        assertEquals("-Dcubeclient.configDir=" + sharedRoot, command.get(1));
        assertEquals("-cp", command.get(2));
        String classpath = command.get(3);
        List<String> classpathEntries = List.of(classpath.split(java.util.regex.Pattern.quote(File.pathSeparator)));
        assertEquals(2, classpathEntries.size());
        // Shared trees must sit directly under sharedRoot, NOT nested inside instances/.
        assertEquals(
            sharedRoot.resolve(Path.of("libraries", "com", "example", "foo", "1.0", "foo-1.0.jar")).toString(),
            classpathEntries.get(0));
        assertEquals(
            sharedRoot.resolve(Path.of("versions", "1.21.4", "1.21.4.jar")).toString(),
            classpathEntries.get(1));
        assertEquals("net.minecraft.client.main.Main", command.get(4));
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
            Session.online("Steve", "abc123uuid", "MC_TOKEN"), InstalledLoader.none());

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
            Session.offline("manual-test"), InstalledLoader.none());

        assertEquals("manual-test", command.get(command.indexOf("--username") + 1));
        assertEquals("legacy", command.get(command.indexOf("--userType") + 1));
    }

    // With a loader installed the game must boot through the loader, not vanilla's main class,
    // and the loader's jars must precede the vanilla ones so its ASM/loader classes win.
    @Test
    void aLoaderReplacesTheMainClassAndLeadsTheClasspath() {
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        Path loaderJar = sharedRoot.resolve(Path.of("libraries", "net", "fabricmc", "fabric-loader.jar"));
        InstalledLoader loader = new InstalledLoader(
            "net.fabricmc.loader.impl.launch.knot.KnotClient", List.of(loaderJar), java.util.Set.of(),
            List.of());

        List<String> command = new JvmArgsBuilder().build(
            new Profile("fabric-1.21", "1.21.4", "fabric", List.of()),
            sampleDetail(),
            sharedRoot.resolve(Path.of("instances", "fabric-1.21")),
            sharedRoot,
            sharedRoot.resolve(Path.of("runtimes", "21", "bin", "java.exe")),
            Session.offline("fabric-1.21"),
            loader);

        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", command.get(4));
        String classpath = command.get(3);
        assertTrue(classpath.startsWith(loaderJar.toString()),
            "loader jars must come first; got " + classpath);
    }

    // The mod resolves its own config location from this property; without it every profile
    // would fall back to Fabric's per-instance config dir, which is exactly the per-version
    // storage the design rejected in favour of one shared file.
    @Test
    void setsTheModConfigDirectoryToTheSharedRoot() {
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        List<String> command = new JvmArgsBuilder().build(
            new Profile("v", "1.21.4", "vanilla", List.of()),
            sampleDetail(),
            sharedRoot.resolve(Path.of("instances", "v")),
            sharedRoot,
            sharedRoot.resolve(Path.of("runtimes", "21", "bin", "java.exe")),
            Session.offline("v"),
            InstalledLoader.none());

        assertTrue(command.contains("-Dcubeclient.configDir=" + sharedRoot),
            "actual: " + command);
    }

    @Test
    void vanillaKeepsTheManifestMainClass() {
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");

        List<String> command = new JvmArgsBuilder().build(
            new Profile("v", "1.21.4", "vanilla", List.of()),
            sampleDetail(),
            sharedRoot.resolve(Path.of("instances", "v")),
            sharedRoot,
            sharedRoot.resolve(Path.of("runtimes", "21", "bin", "java.exe")),
            Session.offline("v"),
            InstalledLoader.none());

        assertEquals("net.minecraft.client.main.Main", command.get(4));
    }

    // Fabric ships its own ASM. Leaving the game's copy on the classpath makes the loader abort
    // with "duplicate ASM classes found on classpath" and the game never opens — found by
    // actually launching, not by any unit test.
    @Test
    void dropsVanillaLibrariesTheLoaderSupersedes() {
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        VersionDetail detail = new VersionDetail(
            "1.21.4",
            "net.minecraft.client.main.Main",
            new VersionDetail.ClientDownload("https://example.com/client.jar", "abc", 100),
            List.of(
                new VersionDetail.Library("org/ow2/asm/asm/9.6/asm-9.6.jar", "u", "d", 1),
                new VersionDetail.Library("com/example/keep/1.0/keep-1.0.jar", "u", "d", 1)),
            new VersionDetail.AssetIndexRef("17", "https://example.com/17.json", "ghi"),
            21);
        Path fabricAsm = sharedRoot.resolve(
            Path.of("libraries", "org", "ow2", "asm", "asm", "9.10.1", "asm-9.10.1.jar"));
        InstalledLoader loader = new InstalledLoader(
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
            List.of(fabricAsm),
            java.util.Set.of("org/ow2/asm/asm"),
            List.of());

        String classpath = new JvmArgsBuilder().build(
            new Profile("fabric-1.21", "1.21.4", "fabric", List.of()),
            detail,
            sharedRoot.resolve(Path.of("instances", "fabric-1.21")),
            sharedRoot,
            sharedRoot.resolve(Path.of("runtimes", "21", "bin", "java.exe")),
            Session.offline("fabric-1.21"),
            loader).get(3);

        assertTrue(classpath.contains("asm-9.10.1.jar"), classpath);
        assertFalse(classpath.contains("asm-9.6.jar"), "vanilla ASM must be dropped: " + classpath);
        assertTrue(classpath.contains("keep-1.0.jar"), "unrelated libraries must survive");
    }
}
