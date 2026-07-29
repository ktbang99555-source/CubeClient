package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.auth.Session;
import com.cubeclient.launcher.download.AssetDownloader;
import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.events.EventEmitter;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.manifest.VersionEntry;
import com.cubeclient.launcher.manifest.VersionManifestFetcher;
import com.cubeclient.launcher.loader.LoaderInstaller;
import com.cubeclient.launcher.profile.Profile;
import com.cubeclient.launcher.runtime.JreProvisioner;

import java.io.IOException;
import java.nio.file.Path;

public class LaunchCommand {
    private final VersionManifestFetcher manifestFetcher;
    private final Downloader downloader;
    private final AssetDownloader assetDownloader;
    private final JvmArgsBuilder argsBuilder;
    private final LoaderInstaller loaderInstaller;
    private final JreProvisioner jreProvisioner;
    private final ProcessRunner processRunner;
    private final EventEmitter events;
    private final ModDeployer modDeployer;

    public LaunchCommand(
        VersionManifestFetcher manifestFetcher,
        Downloader downloader,
        AssetDownloader assetDownloader,
        JvmArgsBuilder argsBuilder,
        LoaderInstaller loaderInstaller,
        JreProvisioner jreProvisioner,
        ProcessRunner processRunner,
        EventEmitter events,
        ModDeployer modDeployer
    ) {
        this.manifestFetcher = manifestFetcher;
        this.downloader = downloader;
        this.assetDownloader = assetDownloader;
        this.argsBuilder = argsBuilder;
        this.loaderInstaller = loaderInstaller;
        this.jreProvisioner = jreProvisioner;
        this.processRunner = processRunner;
        this.events = events;
        this.modDeployer = modDeployer;
    }

    /**
     * @param gameDir    the profile's instance dir, {@code %APPDATA%/CubeClient/instances/<profileId>}
     * @param sharedRoot {@code %APPDATA%/CubeClient}, holding the shared {@code libraries/},
     *                   {@code versions/}, and {@code assets/} trees
     *
     * <p>Downloads MUST land under the same {@code sharedRoot} that {@link JvmArgsBuilder} puts on
     * the classpath. Both take it as an explicit parameter so they cannot disagree.
     */
    public int run(Profile profile, Path gameDir, Path sharedRoot, String osName, Session session,
            Path modJarSource) throws IOException {
        events.progress("manifest", 0);
        var versions = manifestFetcher.fetchVersionList();
        VersionEntry entry = manifestFetcher.findVersion(versions, profile.mcVersion());
        VersionDetail detail = manifestFetcher.fetchVersionDetail(entry);

        events.progress("libraries", 30);
        Path librariesDir = sharedRoot.resolve("libraries");
        for (VersionDetail.Library library : detail.libraries()) {
            downloader.downloadVerified(
                library.url(),
                librariesDir.resolve(library.relativePath()),
                library.sha1()
            );
        }

        events.progress("client_jar", 50);
        Path clientJar = sharedRoot.resolve(Path.of("versions", detail.id(), detail.id() + ".jar"));
        downloader.downloadVerified(detail.clientDownload().url(), clientJar, detail.clientDownload().sha1());

        // Assets are thousands of small files and dominate a first launch, so this stage reports
        // incremental progress rather than a single jump.
        events.progress("assets", 60);
        assetDownloader.downloadAssets(detail.assetIndex(), sharedRoot, (completed, total) -> {
            if (total > 0) {
                events.progress("assets", 60 + (completed * 30 / total));
            }
        });

        // Provisioned only after the manifest is read, because the manifest is what says which
        // Java version this Minecraft version needs. Guessing it from the version number is how
        // 1.21.4 ended up being handed a Java 17 runtime and dying with UnsupportedClassVersionError.
        events.progress("loader", 85);
        var loader = loaderInstaller.install(profile.loader(), profile.mcVersion(), sharedRoot);

        // Jars the loader itself reports as mods — Fabric API, which CubeClient's own mod
        // declares a dependency on. Fabric Loader finds mods by scanning mods/, so a jar merely
        // on the classpath is invisible to it: leaving Fabric API there made the game refuse to
        // start with "requires fabric-api, which is missing".
        for (Path modJar : loader.modJars()) {
            modDeployer.deploy(modJar, gameDir);
        }

        // Only Fabric profiles can load a mod at all, and only when the launcher told us where
        // the mod jar is (it may not have been built yet in a dev checkout — that must not
        // block launching vanilla or Fabric-without-the-modpack).
        if (modJarSource != null && "fabric".equals(profile.loader())) {
            modDeployer.deploy(modJarSource, gameDir);
        }

        events.progress("runtime", 88);
        Path javaBin = jreProvisioner.ensureJre(
            detail.javaMajorVersion(), sharedRoot.resolve("runtimes"), osName);

        events.progress("launching", 90);
        var command = argsBuilder.build(profile, detail, gameDir, sharedRoot, javaBin, session, loader);
        Process process = processRunner.start(command, gameDir);
        events.launched();

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for game process", e);
        }
        events.exited(exitCode);
        return exitCode;
    }
}
