package com.cubeclient.launcher.runtime;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and unpacks a JRE so the launcher does not depend on whatever {@code java} happens
 * to be first on the user's PATH.
 *
 * <p>This is not a convenience. During end-to-end verification the machine's PATH {@code java}
 * was Java 8 while the backend targets Java 17, and the backend died with
 * {@code UnsupportedClassVersionError} before emitting a single event. Minecraft has the same
 * problem in reverse: 1.8.9 needs Java 8, modern versions need 17.
 *
 * <p>Archives come from the Adoptium API. Only the {@code .zip} layout (Windows) is unpacked
 * here; a {@code .tar.gz} branch is needed before this works on Linux or macOS.
 */
public class JreProvisioner {

    private static final String ADOPTIUM_API = "https://api.adoptium.net/v3/assets/latest";

    private final HttpFetcher fetcher;
    private final Downloader downloader;

    public JreProvisioner(HttpFetcher fetcher, Downloader downloader) {
        this.fetcher = fetcher;
        this.downloader = downloader;
    }

    /**
     * @param majorVersion Java feature version, e.g. 8 or 17
     * @param runtimesDir  {@code %APPDATA%/CubeClient/runtimes}
     * @param os           Adoptium's os name, e.g. {@code "windows"}
     * @return path to the {@code java} executable inside the provisioned runtime
     */
    public Path ensureJre(int majorVersion, Path runtimesDir, String os) throws IOException {
        Path installDir = runtimesDir.resolve(String.valueOf(majorVersion));

        Path existing = findJavaBinary(installDir);
        if (existing != null) {
            return existing;
        }

        String listingUrl = ADOPTIUM_API + "/" + majorVersion
            + "/hotspot?architecture=x64&image_type=jre&vendor=eclipse&os=" + os;
        String listing = fetcher.getString(listingUrl);

        JsonObject pkg;
        try {
            JsonArray assets = JsonParser.parseString(listing).getAsJsonArray();
            if (assets.isEmpty()) {
                throw new IOException(
                    "Adoptium has no JRE " + majorVersion + " build for " + os + " (empty listing)");
            }
            pkg = assets.get(0).getAsJsonObject().getAsJsonObject("binary").getAsJsonObject("package");
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(
                "Malformed Adoptium listing for JRE " + majorVersion + " at " + listingUrl, e);
        }

        String link = pkg.get("link").getAsString();
        String checksum = pkg.get("checksum").getAsString();

        Path archive = runtimesDir.resolve(majorVersion + ".download");
        // Adoptium publishes SHA-256, not the SHA-1 that Mojang uses for everything else.
        downloader.downloadVerifiedSha256(link, archive, checksum);

        try {
            unzip(archive, installDir);
        } finally {
            Files.deleteIfExists(archive);
        }

        Path javaBinary = findJavaBinary(installDir);
        if (javaBinary == null) {
            throw new IOException("No java executable found in the JRE " + majorVersion
                + " archive unpacked at " + installDir);
        }
        return javaBinary;
    }

    /** Adoptium nests everything under one versioned directory, so search rather than assume. */
    private Path findJavaBinary(Path installDir) throws IOException {
        if (!Files.isDirectory(installDir)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(installDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> {
                    Path name = p.getFileName();
                    Path parent = p.getParent();
                    return parent != null
                        && "bin".equals(parent.getFileName().toString())
                        && (name.toString().equals("java") || name.toString().equals("java.exe"));
                })
                // Deterministic pick if an archive ever contains more than one.
                .min(Comparator.comparing(Path::toString))
                .orElse(null);
        }
    }

    private void unzip(Path archive, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path targetRoot = targetDir.toAbsolutePath().normalize();

        try (InputStream in = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path resolved = targetRoot.resolve(entry.getName()).normalize();
                // Zip-slip guard: an entry named "../../evil" would otherwise be written
                // anywhere on disk the process can reach.
                if (!resolved.startsWith(targetRoot)) {
                    throw new IOException(
                        "Refusing archive entry that escapes the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zip, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
