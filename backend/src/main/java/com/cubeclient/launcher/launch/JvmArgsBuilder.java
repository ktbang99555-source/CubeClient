package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.profile.Profile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JvmArgsBuilder {

    /**
     * @param gameDir    the profile's own instance directory, {@code %APPDATA%/CubeClient/instances/<profileId>}
     * @param sharedRoot {@code %APPDATA%/CubeClient} — the parent holding the shared
     *                   {@code libraries/}, {@code versions/}, and {@code assets/} trees that every
     *                   profile draws from. Passed explicitly rather than derived from {@code gameDir}
     *                   by path arithmetic, so the layout is stated once and cannot drift.
     */
    public List<String> build(Profile profile, VersionDetail detail, Path gameDir, Path sharedRoot, Path javaBin) {
        List<String> command = new ArrayList<>();
        command.add(javaBin.toString());
        command.add("-cp");
        command.add(buildClasspath(detail, sharedRoot));
        command.add(detail.mainClass());
        command.add("--username");
        command.add(profile.id());
        command.add("--version");
        command.add(profile.mcVersion());
        command.add("--gameDir");
        command.add(gameDir.toString());
        command.add("--assetsDir");
        command.add(sharedRoot.resolve("assets").toString());
        return command;
    }

    private String buildClasspath(VersionDetail detail, Path sharedRoot) {
        List<String> entries = detail.libraries().stream()
            .map(library -> sharedRoot.resolve("libraries").resolve(library.relativePath()).toString())
            .collect(Collectors.toCollection(ArrayList::new));
        entries.add(sharedRoot.resolve(Path.of("versions", detail.id(), detail.id() + ".jar")).toString());
        return String.join(File.pathSeparator, entries);
    }
}
