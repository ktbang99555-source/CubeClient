package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.auth.Session;
import com.cubeclient.launcher.loader.LoaderInstaller;
import com.cubeclient.launcher.loader.LoaderInstaller.InstalledLoader;
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
    public List<String> build(Profile profile, VersionDetail detail, Path gameDir, Path sharedRoot,
                              Path javaBin, Session session, InstalledLoader loader) {
        List<String> command = new ArrayList<>();
        command.add(javaBin.toString());
        command.add("-cp");
        command.add(buildClasspath(detail, sharedRoot, loader));
        // Fabric boots through its own launcher, which then loads Minecraft. Keeping the vanilla
        // main class would start the game with none of the profile's mods loaded.
        command.add(loader.isEmpty() ? detail.mainClass() : loader.mainClass());
        command.add("--version");
        command.add(profile.mcVersion());
        command.add("--gameDir");
        command.add(gameDir.toString());
        command.add("--assetsDir");
        command.add(sharedRoot.resolve("assets").toString());
        // Names which index under assets/indexes/ to read. Without it the game cannot resolve a
        // single content-addressed asset and starts with no sound and no text.
        command.add("--assetIndex");
        command.add(detail.assetIndex().id());

        // Identity. Servers validate the access token against Mojang's session service, so an
        // offline session can start the game but cannot join anything.
        command.add("--username");
        command.add(session.username());
        command.add("--uuid");
        command.add(session.uuid());
        command.add("--accessToken");
        command.add(session.accessToken());
        command.add("--userType");
        command.add(session.online() ? "msa" : "legacy");

        return command;
    }

    private String buildClasspath(VersionDetail detail, Path sharedRoot, InstalledLoader loader) {
        List<String> entries = new ArrayList<>();
        // Loader jars go first: Fabric's ASM and loader classes must win over anything the
        // vanilla library set happens to also ship.
        loader.extraClasspath().forEach(path -> entries.add(path.toString()));
        detail.libraries().stream()
            // Drop vanilla builds the loader replaces. Fabric aborts at startup if it finds two
            // versions of ASM on the classpath, so this is required, not an optimisation.
            .filter(library ->
                !loader.supersededModules().contains(LoaderInstaller.moduleKey(library.relativePath())))
            .map(library -> sharedRoot.resolve("libraries").resolve(library.relativePath()).toString())
            .forEach(entries::add);
        entries.add(sharedRoot.resolve(Path.of("versions", detail.id(), detail.id() + ".jar")).toString());
        return String.join(File.pathSeparator, entries);
    }
}
