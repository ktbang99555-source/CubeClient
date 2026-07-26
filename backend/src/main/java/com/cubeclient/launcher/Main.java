package com.cubeclient.launcher;

import com.cubeclient.launcher.events.EventEmitter;
import com.cubeclient.launcher.profile.Profile;
import com.cubeclient.launcher.profile.ProfileStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length == 0) {
            System.out.println("{\"type\":\"error\",\"message\":\"no subcommand given\"}");
            return 1;
        }

        EventEmitter events = new EventEmitter(System.out);

        // Safety net: the design spec requires that every backend failure reach the UI as a
        // JSON error event. Subcommand handlers catch IOException themselves, but unchecked
        // exceptions (e.g. VersionManifestFetcher.findVersion throws IllegalArgumentException
        // for an unknown Minecraft version) would otherwise escape and kill the process with a
        // stack trace on stderr, which Electron cannot parse. Convert anything that escapes.
        try {
            return dispatch(args, events);
        } catch (RuntimeException e) {
            events.error("cli", e.getClass().getSimpleName() + ": " + e.getMessage());
            return 1;
        }
    }

    private static int dispatch(String[] args, EventEmitter events) {
        switch (args[0]) {
            case "ping" -> {
                System.out.println("{\"type\":\"pong\"}");
                return 0;
            }
            case "list-profiles" -> {
                return runListProfiles(events);
            }
            default -> {
                events.error("cli", "unknown subcommand: " + args[0]);
                return 1;
            }
        }
    }

    private static int runListProfiles(EventEmitter events) {
        try {
            Path profilesPath = appDataDir().resolve("profiles.json");
            ProfileStore store = new ProfileStore(profilesPath);
            List<Profile> profiles = store.loadAll();
            events.profiles(profiles);
            return 0;
        } catch (IOException e) {
            events.error("list-profiles", e.getMessage());
            return 1;
        }
    }

    static Path appDataDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("CubeClient");
    }
}
