package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.profile.Profile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JvmArgsBuilder {

    public List<String> build(Profile profile, VersionDetail detail, Path gameDir, Path javaBin) {
        List<String> command = new ArrayList<>();
        command.add(javaBin.toString());
        command.add("-cp");
        command.add(buildClasspath(detail, gameDir));
        command.add(detail.mainClass());
        command.add("--username");
        command.add(profile.id());
        command.add("--version");
        command.add(profile.mcVersion());
        command.add("--gameDir");
        command.add(gameDir.toString());
        command.add("--assetsDir");
        command.add(gameDir.resolveSibling("assets").toString());
        return command;
    }

    private String buildClasspath(VersionDetail detail, Path gameDir) {
        List<String> entries = detail.libraries().stream()
            .map(library -> gameDir.resolveSibling(Path.of("libraries", library.relativePath())).toString())
            .collect(Collectors.toCollection(ArrayList::new));
        entries.add(gameDir.resolveSibling(Path.of("versions", detail.id(), detail.id() + ".jar")).toString());
        return String.join(File.pathSeparator, entries);
    }
}
