package com.cubeclient.launcher.launch;

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
            new VersionDetail.AssetIndexRef("17", "https://example.com/17.json", "ghi")
        );
        Profile profile = new Profile("latest-1.21", "1.21.4", "vanilla", List.of());
        Path gameDir = Path.of("C:", "AppData", "CubeClient", "instances", "latest-1.21");
        Path javaBin = Path.of("C:", "AppData", "CubeClient", "runtimes", "17", "bin", "java.exe");

        List<String> command = new JvmArgsBuilder().build(profile, detail, gameDir, javaBin);

        assertEquals(javaBin.toString(), command.get(0));
        assertEquals("-cp", command.get(1));
        String classpath = command.get(2);
        assertTrue(classpath.contains("foo-1.0.jar"));
        assertTrue(classpath.contains("1.21.4.jar"));
        assertTrue(classpath.split(File.pathSeparator.equals(";") ? ";" : ":").length == 2);
        assertEquals("net.minecraft.client.main.Main", command.get(3));
        assertTrue(command.contains("--version"));
        assertTrue(command.contains("1.21.4"));
        assertTrue(command.contains("--gameDir"));
    }
}
