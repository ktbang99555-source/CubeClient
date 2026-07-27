package com.cubeclient.launcher;

import com.cubeclient.launcher.auth.Session;
import com.cubeclient.launcher.download.AssetDownloader;
import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.events.EventEmitter;
import com.cubeclient.launcher.http.JavaHttpFetcher;
import com.cubeclient.launcher.launch.JvmArgsBuilder;
import com.cubeclient.launcher.launch.LaunchCommand;
import com.cubeclient.launcher.launch.RealProcessRunner;
import com.cubeclient.launcher.manifest.VersionManifestFetcher;
import com.cubeclient.launcher.profile.Profile;
import com.cubeclient.launcher.profile.ProfileStore;
import com.cubeclient.launcher.runtime.JreProvisioner;

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
            case "launch" -> {
                return runLaunch(args, events);
            }
            case "login" -> {
                return runLogin(events);
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
            // Seeds a default set when the file is absent. An empty launcher window has no
            // way to explain that there is nothing to launch, and requiring a hand-written
            // JSON file before the program does anything is not a reasonable first run.
            List<Profile> profiles = store.loadOrSeedDefaults();
            events.profiles(profiles);
            return 0;
        } catch (IOException e) {
            events.error("list-profiles", e.getMessage());
            return 1;
        }
    }

    private static int runLaunch(String[] args, EventEmitter events) {
        if (args.length < 2) {
            events.error("cli", "launch requires a profile id argument");
            return 1;
        }
        String profileId = args[1];
        try {
            Path appData = appDataDir();
            ProfileStore profileStore = new ProfileStore(appData.resolve("profiles.json"));
            Profile profile = profileStore.loadAll().stream()
                .filter(p -> p.id().equals(profileId))
                .findFirst()
                .orElse(null);
            if (profile == null) {
                events.error("launch", "unknown profile: " + profileId);
                return 1;
            }

            var fetcher = new JavaHttpFetcher();
            var manifestFetcher = new VersionManifestFetcher(fetcher);
            var downloader = new Downloader(fetcher);
            var assetDownloader = new AssetDownloader(fetcher, downloader);
            var argsBuilder = new JvmArgsBuilder();
            var processRunner = new RealProcessRunner();
            var loaderInstaller = new com.cubeclient.launcher.loader.LoaderInstaller(fetcher, downloader);
            var jreProvisioner = new JreProvisioner(fetcher, downloader);
            var launchCommand = new LaunchCommand(manifestFetcher, downloader, assetDownloader,
                argsBuilder, loaderInstaller, jreProvisioner, processRunner, events);

            Path gameDir = appData.resolve("instances").resolve(profile.id());

            return launchCommand.run(
                profile, gameDir, appData, adoptiumOsName(), readSession(profile));
        } catch (IOException e) {
            events.error("launch", e.getMessage());
            return 1;
        }
    }

    private static int runLogin(EventEmitter events) {
        try {
            var fetcher = new com.cubeclient.launcher.http.JavaHttpFetcher();
            var authClient = new com.cubeclient.launcher.auth.MicrosoftAuthClient(fetcher);
            var deviceCode = authClient.requestDeviceCode();
            events.deviceCode(deviceCode.userCode(), deviceCode.verificationUri());
            var result = authClient.pollForMinecraftAuth(deviceCode);
            // Distinct event type, not "login_success": Electron consumes this one to encrypt the
            // token and must never forward it to the renderer. Keeping the credential on its own
            // event type means the forwarding rule is an allowlist, not a field-stripping step
            // that is easy to forget.
            events.authResult(result.username(), result.uuid(), result.accessToken());
            return 0;
        } catch (IOException e) {
            events.error("login", e.getMessage());
            return 1;
        }
    }

    /**
     * Reads the account to launch as from a single JSON line on stdin.
     *
     * <p>The token arrives over stdin rather than as a command-line argument because arguments
     * are visible to any process that can list the process table. Electron holds the encrypted
     * token and writes this line; when nothing is piped in, the launch falls back to an offline
     * session that starts the game but cannot join servers.
     */
    private static Session readSession(Profile profile) throws IOException {
        String line = new java.io.BufferedReader(
            new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8)).readLine();
        if (line == null || line.isBlank()) {
            return Session.offline(profile.id());
        }
        try {
            var json = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
            return Session.online(
                json.get("username").getAsString(),
                json.get("uuid").getAsString(),
                json.get("accessToken").getAsString());
        } catch (RuntimeException e) {
            // Deliberately does not echo the line back — it holds a credential.
            throw new IOException("Malformed session on stdin", e);
        }
    }

    /** Adoptium's os names differ from {@code os.name}; map the three we can build for. */
    private static String adoptiumOsName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        return "linux";
    }

    static Path appDataDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("CubeClient");
    }
}
