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
 * <p>Fabric and Legacy Fabric publish an identical profile shape at different hosts — Legacy
 * Fabric exists precisely because upstream Fabric dropped support for versions like 1.8.9 — so
 * one installer serves both and only the meta host differs.
 *
 * <p>Their libraries are Maven coordinates plus a repository root, not the pre-resolved,
 * pre-hashed download entries Mojang publishes. Each coordinate is translated into a repository
 * path, and the digest comes from the repository's own {@code .sha1} sibling so loader jars are
 * verified to the same standard as everything else.
 */
public class LoaderInstaller {

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String LEGACY_FABRIC_META = "https://meta.legacyfabric.net/v2";

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
        Set<String> supersededModules
    ) {
        public static InstalledLoader none() {
            return new InstalledLoader(null, List.of(), Set.of());
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
     * @param loader     one of {@code vanilla}, {@code fabric}, {@code legacyfabric}
     * @param sharedRoot {@code %APPDATA%/CubeClient}; loader jars join the shared library tree
     */
    public InstalledLoader install(String loader, String mcVersion, Path sharedRoot)
            throws IOException {
        String metaHost = switch (loader) {
            case "vanilla" -> null;
            case "fabric" -> FABRIC_META;
            case "legacyfabric" -> LEGACY_FABRIC_META;
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
        return new InstalledLoader(mainClass, classpath, superseded);
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
     * under the repository, which is the standard Maven layout both hosts serve.
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
