package com.cubeclient.launcher.loader;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Installs a Fabric-family mod loader for a profile.
 *
 * <p>Only upstream Fabric is supported, which covers the whole 1.20.x-1.21.x range this
 * launcher targets. Legacy Fabric (1.8.9 and similar) was removed together with support for
 * those versions: they publish native libraries as classifier artifacts that must be unpacked
 * and pointed at with {@code -Djava.library.path}, which this launcher does not do.
 *
 * <p>Fabric libraries are Maven coordinates plus a repository root, not the pre-resolved,
 * pre-hashed download entries Mojang publishes. Each coordinate is translated into a repository
 * path, and the digest comes from the repository's own {@code .sha1} sibling so loader jars are
 * verified to the same standard as everything else.
 */
public class LoaderInstaller {

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";

    // meta.fabricmc.net is Fabric's metadata API host, not its Maven host: every existing
    // downloadLibrary call above gets its repository root from the "url" field inside Fabric's
    // own profile JSON (see PROFILE's library entries in the test), and that field always points
    // at maven.fabricmc.net, never at meta.fabricmc.net. Fabric API is published to that same
    // Maven host, so this is used here rather than deriving a (wrong) root from FABRIC_META.
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";

    // Fabric API has no meta.fabricmc.net version-list endpoint the way the loader and Yarn
    // mappings do, so this cannot be resolved dynamically the way newestLoaderVersion() resolves
    // the loader version. Pinned to the same build the mod project itself compiles against
    // (mod/gradle.properties' fabric_version) — the two must agree, since the mod is compiled
    // against this jar and this is what puts it on the classpath at launch.
    private static final String FABRIC_API_VERSION = "0.119.4+1.21.4";
    private static final String FABRIC_API_COORDINATE =
        "net.fabricmc.fabric-api:fabric-api:" + FABRIC_API_VERSION;

    /**
     * The result of installing a loader.
     *
     * @param mainClass      the class the game must boot through, or null for vanilla
     * @param extraClasspath loader jars to prepend to the vanilla classpath
     */
    public record InstalledLoader(
        String mainClass,
        List<Path> extraClasspath,
        /**
         * {@code group/artifact} keys the loader supplies its own build of.
         *
         * <p>The vanilla library set must drop these. Fabric ships a newer ASM than the game
         * does, and having both on the classpath makes its loader abort at startup with
         * "duplicate ASM classes found on classpath" — the game never opens.
         */
        Set<String> supersededModules,

        /**
         * Jars that are <em>mods</em>, to be copied into the profile's {@code mods/} folder.
         *
         * <p>Separate from {@link #extraClasspath} because Fabric Loader discovers mods by
         * scanning that folder, not by reading the classpath. Fabric API sat on the classpath
         * at first and the game refused to start with "requires fabric-api, which is missing"
         * — the jar was right there and the loader could not see it.
         *
         * <p>They must not also be on the classpath. Loading the same jar through both routes
         * is how the duplicate-class failures this launcher already hit with ASM happen.
         */
        List<Path> modJars
    ) {
        public static InstalledLoader none() {
            return new InstalledLoader(null, List.of(), Set.of(), List.of());
        }

        public boolean isEmpty() {
            return mainClass == null;
        }
    }

    /**
     * Reduces a Maven-style relative path to its {@code group/artifact} prefix by dropping the
     * trailing version directory and file name — {@code org/ow2/asm/asm/9.6/asm-9.6.jar} becomes
     * {@code org/ow2/asm/asm}. This is what makes two different versions of one library
     * comparable.
     */
    public static String moduleKey(String relativePath) {
        String normalized = relativePath.replace(java.io.File.separatorChar, '/');
        int fileSeparator = normalized.lastIndexOf('/');
        if (fileSeparator < 0) return normalized;
        int versionSeparator = normalized.lastIndexOf('/', fileSeparator - 1);
        return versionSeparator < 0 ? normalized : normalized.substring(0, versionSeparator);
    }

    private final HttpFetcher fetcher;
    private final Downloader downloader;

    public LoaderInstaller(HttpFetcher fetcher, Downloader downloader) {
        this.fetcher = fetcher;
        this.downloader = downloader;
    }

    /**
     * @param loader     either {@code vanilla} or {@code fabric}
     * @param sharedRoot {@code %APPDATA%/CubeClient}; loader jars join the shared library tree
     */
    public InstalledLoader install(String loader, String mcVersion, Path sharedRoot)
            throws IOException {
        String metaHost = switch (loader) {
            case "vanilla" -> null;
            case "fabric" -> FABRIC_META;
            // Rejected rather than treated as vanilla: silently dropping the loader would start
            // the game with none of the profile's mods and no indication why.
            default -> throw new IOException("Unsupported mod loader: " + loader);
        };
        if (metaHost == null) {
            return InstalledLoader.none();
        }

        String loaderVersion = newestLoaderVersion(metaHost, mcVersion);
        String profileUrl = metaHost + "/versions/loader/" + mcVersion + "/" + loaderVersion
            + "/profile/json";

        JsonObject profile = parse(fetcher.getString(profileUrl), profileUrl);
        String mainClass = profile.get("mainClass").getAsString();

        List<Path> classpath = new ArrayList<>();
        Set<String> superseded = new LinkedHashSet<>();
        JsonArray libraries = profile.getAsJsonArray("libraries");
        if (libraries != null) {
            for (var element : libraries) {
                JsonObject library = element.getAsJsonObject();
                String coordinate = library.get("name").getAsString();
                classpath.add(downloadLibrary(
                    coordinate, library.get("url").getAsString(), sharedRoot));
                superseded.add(moduleKey(relativePathFor(coordinate)));
            }
        }

        // Fabric API is a separate distribution from the loader itself, published to the same
        // Maven host, so it reuses downloadLibrary rather than introducing a second HTTP
        // mechanism. Every Fabric profile needs it — CubeClient's own mod depends on the
        // ScreenEvents and HudRenderCallback APIs it provides.
        //
        // It is a MOD, not a library: reported in modJars so it gets copied into the profile's
        // mods/ folder. Putting it on the classpath instead is what made the game refuse to
        // start with "requires fabric-api, which is missing" — Fabric Loader only discovers
        // mods by scanning that folder.
        List<Path> modJars = List.of(downloadLibrary(FABRIC_API_COORDINATE, FABRIC_MAVEN, sharedRoot));

        return new InstalledLoader(mainClass, classpath, superseded, modJars);
    }

    private String newestLoaderVersion(String metaHost, String mcVersion) throws IOException {
        String url = metaHost + "/versions/loader/" + mcVersion;
        String body = fetcher.getString(url);

        JsonArray entries;
        try {
            entries = JsonParser.parseString(body).getAsJsonArray();
        } catch (RuntimeException e) {
            throw new IOException("Malformed loader list at " + url, e);
        }
        if (entries.isEmpty()) {
            throw new IOException(
                "No mod loader build is available for Minecraft " + mcVersion + " (" + url + ")");
        }
        // The meta API returns newest first.
        return entries.get(0).getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
    }

    /**
     * Resolves {@code group.id:artifact:version} to {@code group/id/artifact/version/artifact-version.jar}
     * under the repository, which is the standard Maven layout Fabric's host serves.
     */
    private Path downloadLibrary(String coordinate, String repositoryUrl, Path sharedRoot)
            throws IOException {
        String relative = relativePathFor(coordinate);
        String jarUrl = repositoryUrl.endsWith("/")
            ? repositoryUrl + relative
            : repositoryUrl + "/" + relative;

        Path destination = sharedRoot.resolve("libraries");
        for (String segment : relative.split("/")) {
            destination = destination.resolve(segment);
        }

        // Maven publishes the digest beside the artifact. Trimmed because the file conventionally
        // ends with a newline, and some repositories append the filename after the hash.
        String sha1 = fetcher.getString(jarUrl + ".sha1").trim().split("\\s+")[0];

        downloader.downloadVerified(jarUrl, destination, sha1);
        return destination;
    }

    private static String relativePathFor(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Unparseable Maven coordinate: " + coordinate);
        }
        String groupPath = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        return groupPath + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
    }

    private JsonObject parse(String body, String url) throws IOException {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Malformed loader profile at " + url, e);
        }
    }
}
