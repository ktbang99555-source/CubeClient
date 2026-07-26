# CubeClient Launcher Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the CubeClient launcher core — an Electron UI that spawns a Java backend to handle Microsoft login, Mojang version manifests, downloads, and launching Minecraft (vanilla, Fabric, or Legacy Fabric profiles).

**Architecture:** Electron (renderer + main process) is UI-only. A separate Java backend, built as a standalone jar, is spawned by Electron's main process per subcommand (`list-profiles`, `login`, `launch`) and streams newline-delimited JSON events on stdout that Electron parses to update the UI. All game/version/auth logic lives in the Java backend so it can be unit-tested without Electron.

**Tech Stack:** Java 17 + Gradle (backend), Gson for JSON, JUnit 5 for backend tests. Node.js + Electron (UI), plain JavaScript, Jest for UI tests.

## Global Constraints

- Supported profile loaders: `vanilla`, `fabric`, `legacyfabric` (spec: profile model in `docs/superpowers/specs/2026-07-26-launcher-core-design.md`).
- Supported Minecraft version range for this sub-project's manual verification: 1.20.x–1.21.x. (1.8.9/Legacy Fabric profile *data model* must work end-to-end for `vanilla`-style launch, since Sub-projects B/C add the actual mod jars later.)
- All persistent data lives under `%APPDATA%/CubeClient/` (spec: profiles.json, auth.json, runtimes/, versions/, libraries/, assets/, instances/<profileId>/).
- Downloads must be SHA1-verified against the manifest; mismatches trigger re-download, not silent acceptance.
- Electron and the Java backend communicate only via stdout JSON lines — no local HTTP/WebSocket server.
- No real network calls in automated tests — all HTTP interaction goes through an injectable `HttpFetcher` interface (backend) or injectable `spawn` function (UI), backed by fakes in tests.

---

## File Structure

```
CubeClient/
├─ backend/
│  ├─ build.gradle.kts
│  ├─ settings.gradle.kts
│  └─ src/
│     ├─ main/java/com/cubeclient/launcher/
│     │  ├─ Main.java                          # CLI entrypoint, subcommand dispatch
│     │  ├─ http/HttpFetcher.java               # interface
│     │  ├─ http/JavaHttpFetcher.java            # real implementation (java.net.http)
│     │  ├─ manifest/VersionEntry.java
│     │  ├─ manifest/VersionDetail.java
│     │  ├─ manifest/VersionManifestFetcher.java
│     │  ├─ download/ChecksumVerifier.java
│     │  ├─ download/Downloader.java
│     │  ├─ profile/Profile.java
│     │  ├─ profile/ProfileStore.java
│     │  ├─ events/EventEmitter.java
│     │  ├─ launch/JvmArgsBuilder.java
│     │  ├─ launch/ProcessRunner.java            # interface
│     │  ├─ launch/RealProcessRunner.java
│     │  ├─ launch/LaunchCommand.java
│     │  └─ auth/MicrosoftAuthClient.java
│     └─ test/java/com/cubeclient/launcher/
│        ├─ manifest/VersionManifestFetcherTest.java
│        ├─ download/ChecksumVerifierTest.java
│        ├─ download/DownloaderTest.java
│        ├─ profile/ProfileStoreTest.java
│        ├─ events/EventEmitterTest.java
│        ├─ launch/JvmArgsBuilderTest.java
│        ├─ launch/LaunchCommandTest.java
│        └─ auth/MicrosoftAuthClientTest.java
├─ ui/
│  ├─ package.json
│  ├─ main.js                                   # Electron main process
│  ├─ preload.js
│  ├─ src/backendProcess.js                      # spawns jar, parses JSON lines
│  ├─ renderer/index.html
│  ├─ renderer/renderer.js                       # profile list + progress rendering
│  └─ test/
│     ├─ backendProcess.test.js
│     └─ renderer.test.js
└─ docs/superpowers/{specs,plans}/...
```

Each backend class has one responsibility (fetch manifest, verify checksum, download, store profiles, emit events, build JVM args, run a process, authenticate). `LaunchCommand` is the only class that wires them together, so it's the integration point and the one hardest to unit-test — its test uses fakes for every collaborator.

---

## Task 1: Scaffold Gradle backend and Electron UI projects

**Files:**
- Create: `backend/build.gradle.kts`
- Create: `backend/settings.gradle.kts`
- Create: `backend/gradle.properties`
- Create: `backend/src/main/java/com/cubeclient/launcher/Main.java`
- Create: `backend/src/test/java/com/cubeclient/launcher/MainSmokeTest.java`
- Create: `ui/package.json`
- Create: `ui/main.js`
- Create: `ui/renderer/index.html`
- Create: `.gitignore`

**Interfaces:**
- Produces: `Main.main(String[] args)` — dispatches on `args[0]`, currently only handles `"ping"` → prints `{"type":"pong"}` to stdout, exit code 0. Later tasks add real subcommands here.
- Produces: Electron app that opens a blank window loading `renderer/index.html`.

- [ ] **Step 1: Create Gradle backend project**

```bash
mkdir -p "backend/src/main/java/com/cubeclient/launcher"
mkdir -p "backend/src/test/java/com/cubeclient/launcher"
```

`backend/settings.gradle.kts`:
```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "cubeclient-launcher-backend"
```

Note: this machine only has Java 8 installed, and the backend targets Java 17 (see `build.gradle.kts` below). The `foojay-resolver-convention` plugin lets Gradle auto-download a matching JDK 17 toolchain the first time `./gradlew` runs, instead of failing with "no compatible toolchain found". This requires internet access on first build; the downloaded JDK is cached under `~/.gradle/jdks/` for all subsequent builds.

`backend/build.gradle.kts`:
```kotlin
plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Required explicitly: Gradle 8.x deprecates auto-loading the test framework's
    // runtime, and omitting this makes every build print a "Deprecated Gradle
    // features were used in this build" warning.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.cubeclient.launcher.Main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.cubeclient.launcher.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

`backend/gradle.properties`:
```
org.gradle.jvmargs=-Xmx512m
```

`backend/src/main/java/com/cubeclient/launcher/Main.java`:
```java
package com.cubeclient.launcher;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("{\"type\":\"error\",\"message\":\"no subcommand given\"}");
            System.exit(1);
        }
        if ("ping".equals(args[0])) {
            System.out.println("{\"type\":\"pong\"}");
            System.exit(0);
        }
        System.out.println("{\"type\":\"error\",\"message\":\"unknown subcommand: " + args[0] + "\"}");
        System.exit(1);
    }
}
```

- [ ] **Step 2: Write smoke test**

`backend/src/test/java/com/cubeclient/launcher/MainSmokeTest.java`:
```java
package com.cubeclient.launcher;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainSmokeTest {
    @Test
    void pingPrintsPongJson() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured));
        try {
            Main.main(new String[] { "ping" });
        } catch (SecurityException ignored) {
            // System.exit under test runner may throw depending on setup; ignore for this smoke check
        } finally {
            System.setOut(original);
        }
        assertTrue(captured.toString().contains("\"type\":\"pong\""));
    }
}
```

Note: `System.exit(0)` inside `main` will terminate the JVM if actually invoked by the test runner without a security manager trick. To keep this smoke test simple and non-fatal, change `Main.main` to call a package-private `run(String[] args)` that returns an exit code, and have `main` call `System.exit(run(args))`. Apply that now:

```java
package com.cubeclient.launcher;

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
        if ("ping".equals(args[0])) {
            System.out.println("{\"type\":\"pong\"}");
            return 0;
        }
        System.out.println("{\"type\":\"error\",\"message\":\"unknown subcommand: " + args[0] + "\"}");
        return 1;
    }
}
```

And the test calls `Main.run(new String[] { "ping" })` instead of `Main.main`:

```java
package com.cubeclient.launcher;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainSmokeTest {
    @Test
    void pingPrintsPongJsonAndReturnsZero() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured));
        int exitCode;
        try {
            exitCode = Main.run(new String[] { "ping" });
        } finally {
            System.setOut(original);
        }
        assertEquals(0, exitCode);
        assertTrue(captured.toString().contains("\"type\":\"pong\""));
    }
}
```

- [ ] **Step 3: Run the backend test suite**

Run: `cd backend && ./gradlew test` (on Windows: `gradlew.bat test`; if the wrapper isn't present yet, run `gradle wrapper` once with a locally installed Gradle first, then commit the wrapper files)
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 4: Scaffold Electron UI project**

`ui/package.json`:
```json
{
  "name": "cubeclient-ui",
  "version": "0.1.0",
  "main": "main.js",
  "scripts": {
    "start": "electron .",
    "test": "jest"
  },
  "devDependencies": {
    "electron": "^31.0.0",
    "jest": "^29.7.0",
    "jest-environment-jsdom": "^29.7.0"
  }
}
```

`ui/main.js`:
```js
const { app, BrowserWindow } = require('electron');
const path = require('path');

function createWindow() {
  const win = new BrowserWindow({
    width: 1000,
    height: 650,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
```

`ui/preload.js`:
```js
// Bridges main <-> renderer; extended in Task 9/10.
```

`ui/renderer/index.html`:
```html
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>CubeClient</title></head>
<body>
  <div id="app"></div>
  <script src="renderer.js"></script>
</body>
</html>
```

`ui/renderer/renderer.js`:
```js
// Populated starting in Task 10.
```

`.gitignore` (repo root):
```
backend/build/
backend/.gradle/
ui/node_modules/
ui/dist/
*.log
```

- [ ] **Step 5: Install UI dependencies and verify Electron boots**

Run: `cd ui && npm install`
Expected: installs without error.

Run: `cd ui && npm start`
Expected: a window opens showing a blank page with title "CubeClient". Close it manually.

- [ ] **Step 6: Commit**

```bash
git add backend ui .gitignore
git commit -m "Scaffold Gradle backend and Electron UI projects"
```

---

## Task 2: Version manifest fetching and parsing

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/http/HttpFetcher.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/http/JavaHttpFetcher.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/manifest/VersionEntry.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/manifest/VersionDetail.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/manifest/VersionManifestFetcher.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/manifest/VersionManifestFetcherTest.java`

**Interfaces:**
- Produces: `HttpFetcher` interface with `String getString(String url)` and `void downloadToFile(String url, Path destination)`, both `throws IOException`. Used by every later task that talks to the network.
- Produces: `VersionEntry(String id, String url)` record.
- Produces: `VersionDetail(String id, String mainClass, ClientDownload clientDownload, List<Library> libraries, AssetIndexRef assetIndex)` with nested records `ClientDownload(String url, String sha1, long size)`, `Library(String relativePath, String url, String sha1, long size)`, `AssetIndexRef(String id, String url, String sha1)`.
- Produces: `VersionManifestFetcher.fetchVersionList()` → `List<VersionEntry>`, and `VersionManifestFetcher.fetchVersionDetail(VersionEntry entry)` → `VersionDetail`. Both `throws IOException`.
- Consumes: nothing from earlier tasks (this is the first real domain logic).

- [ ] **Step 1: Write the HttpFetcher interface**

`backend/src/main/java/com/cubeclient/launcher/http/HttpFetcher.java`:
```java
package com.cubeclient.launcher.http;

import java.io.IOException;
import java.nio.file.Path;

public interface HttpFetcher {
    String getString(String url) throws IOException;
    void downloadToFile(String url, Path destination) throws IOException;
}
```

`backend/src/main/java/com/cubeclient/launcher/http/JavaHttpFetcher.java`:
```java
package com.cubeclient.launcher.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaHttpFetcher implements HttpFetcher {
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public String getString(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("GET " + url + " returned status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    @Override
    public void downloadToFile(String url, Path destination) throws IOException {
        try {
            Files.createDirectories(destination.getParent());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
            if (response.statusCode() != 200) {
                throw new IOException("GET " + url + " returned status " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        }
    }
}
```

- [ ] **Step 2: Write the manifest data types**

`backend/src/main/java/com/cubeclient/launcher/manifest/VersionEntry.java`:
```java
package com.cubeclient.launcher.manifest;

public record VersionEntry(String id, String url) {}
```

`backend/src/main/java/com/cubeclient/launcher/manifest/VersionDetail.java`:
```java
package com.cubeclient.launcher.manifest;

import java.util.List;

public record VersionDetail(
    String id,
    String mainClass,
    ClientDownload clientDownload,
    List<Library> libraries,
    AssetIndexRef assetIndex
) {
    public record ClientDownload(String url, String sha1, long size) {}
    public record Library(String relativePath, String url, String sha1, long size) {}
    public record AssetIndexRef(String id, String url, String sha1) {}
}
```

- [ ] **Step 3: Write the failing test for VersionManifestFetcher**

`backend/src/test/java/com/cubeclient/launcher/manifest/VersionManifestFetcherTest.java`:
```java
package com.cubeclient.launcher.manifest;

import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionManifestFetcherTest {

    static class FakeHttpFetcher implements HttpFetcher {
        private final Map<String, String> responses;
        FakeHttpFetcher(Map<String, String> responses) { this.responses = responses; }

        @Override
        public String getString(String url) {
            String body = responses.get(url);
            if (body == null) throw new IllegalStateException("No fake response for " + url);
            return body;
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            throw new UnsupportedOperationException("not used in this test");
        }
    }

    private static final String MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    @Test
    void fetchVersionListParsesEntries() throws IOException {
        String manifestJson = """
            {
              "latest": { "release": "1.21.4", "snapshot": "1.21.4" },
              "versions": [
                { "id": "1.21.4", "type": "release", "url": "https://example.com/1.21.4.json" },
                { "id": "1.8.9", "type": "release", "url": "https://example.com/1.8.9.json" }
              ]
            }
            """;
        FakeHttpFetcher fetcher = new FakeHttpFetcher(Map.of(MANIFEST_URL, manifestJson));
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);

        List<VersionEntry> versions = manifestFetcher.fetchVersionList();

        assertEquals(2, versions.size());
        assertEquals(new VersionEntry("1.21.4", "https://example.com/1.21.4.json"), versions.get(0));
        assertEquals(new VersionEntry("1.8.9", "https://example.com/1.8.9.json"), versions.get(1));
    }

    @Test
    void fetchVersionDetailParsesLibrariesAndClientDownload() throws IOException {
        String detailUrl = "https://example.com/1.21.4.json";
        String detailJson = """
            {
              "id": "1.21.4",
              "mainClass": "net.minecraft.client.main.Main",
              "downloads": {
                "client": { "url": "https://example.com/client.jar", "sha1": "abc123", "size": 100 }
              },
              "libraries": [
                {
                  "name": "com.example:foo:1.0",
                  "downloads": {
                    "artifact": {
                      "path": "com/example/foo/1.0/foo-1.0.jar",
                      "url": "https://example.com/foo-1.0.jar",
                      "sha1": "def456",
                      "size": 50
                    }
                  }
                }
              ],
              "assetIndex": { "id": "17", "url": "https://example.com/17.json", "sha1": "ghi789" }
            }
            """;
        FakeHttpFetcher fetcher = new FakeHttpFetcher(Map.of(detailUrl, detailJson));
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);

        VersionDetail detail = manifestFetcher.fetchVersionDetail(new VersionEntry("1.21.4", detailUrl));

        assertEquals("1.21.4", detail.id());
        assertEquals("net.minecraft.client.main.Main", detail.mainClass());
        assertEquals("https://example.com/client.jar", detail.clientDownload().url());
        assertEquals("abc123", detail.clientDownload().sha1());
        assertEquals(1, detail.libraries().size());
        assertEquals("com/example/foo/1.0/foo-1.0.jar", detail.libraries().get(0).relativePath());
        assertEquals("17", detail.assetIndex().id());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.VersionManifestFetcherTest"`
Expected: FAIL — `VersionManifestFetcher` does not exist yet (compile error).

- [ ] **Step 5: Implement VersionManifestFetcher**

`backend/src/main/java/com/cubeclient/launcher/manifest/VersionManifestFetcher.java`:
```java
package com.cubeclient.launcher.manifest;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VersionManifestFetcher {
    private static final String MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final HttpFetcher fetcher;

    public VersionManifestFetcher(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public List<VersionEntry> fetchVersionList() throws IOException {
        String body = fetcher.getString(MANIFEST_URL);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray versionsArray = root.getAsJsonArray("versions");
        List<VersionEntry> versions = new ArrayList<>();
        for (int i = 0; i < versionsArray.size(); i++) {
            JsonObject entry = versionsArray.get(i).getAsJsonObject();
            versions.add(new VersionEntry(entry.get("id").getAsString(), entry.get("url").getAsString()));
        }
        return versions;
    }

    public VersionDetail fetchVersionDetail(VersionEntry entry) throws IOException {
        String body = fetcher.getString(entry.url());
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();

        String id = root.get("id").getAsString();
        String mainClass = root.get("mainClass").getAsString();

        JsonObject clientDownloadJson = root.getAsJsonObject("downloads").getAsJsonObject("client");
        VersionDetail.ClientDownload clientDownload = new VersionDetail.ClientDownload(
            clientDownloadJson.get("url").getAsString(),
            clientDownloadJson.get("sha1").getAsString(),
            clientDownloadJson.get("size").getAsLong()
        );

        List<VersionDetail.Library> libraries = new ArrayList<>();
        for (var libraryElement : root.getAsJsonArray("libraries")) {
            JsonObject libraryJson = libraryElement.getAsJsonObject();
            JsonObject downloads = libraryJson.getAsJsonObject("downloads");
            if (!downloads.has("artifact")) continue;
            JsonObject artifact = downloads.getAsJsonObject("artifact");
            libraries.add(new VersionDetail.Library(
                artifact.get("path").getAsString(),
                artifact.get("url").getAsString(),
                artifact.get("sha1").getAsString(),
                artifact.get("size").getAsLong()
            ));
        }

        JsonObject assetIndexJson = root.getAsJsonObject("assetIndex");
        VersionDetail.AssetIndexRef assetIndex = new VersionDetail.AssetIndexRef(
            assetIndexJson.get("id").getAsString(),
            assetIndexJson.get("url").getAsString(),
            assetIndexJson.get("sha1").getAsString()
        );

        return new VersionDetail(id, mainClass, clientDownload, libraries, assetIndex);
    }

    public VersionEntry findVersion(List<VersionEntry> versions, String mcVersion) {
        return versions.stream()
            .filter(v -> v.id().equals(mcVersion))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown Minecraft version: " + mcVersion));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.VersionManifestFetcherTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/http backend/src/main/java/com/cubeclient/launcher/manifest backend/src/test/java/com/cubeclient/launcher/manifest
git commit -m "Add version manifest fetching and parsing"
```

---

## Task 3: Checksum verification and verified downloader

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/download/ChecksumVerifier.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/download/Downloader.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/download/ChecksumVerifierTest.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/download/DownloaderTest.java`

**Interfaces:**
- Consumes: `HttpFetcher` (Task 2).
- Produces: `ChecksumVerifier.matchesSha1(Path file, String expectedSha1)` → `boolean`, `throws IOException`.
- Produces: `Downloader(HttpFetcher fetcher)` constructor, `downloadVerified(String url, Path destination, String expectedSha1)` → `void`, `throws IOException`. Skips download if `destination` already exists and matches; downloads then re-verifies otherwise, throwing `IOException` on mismatch after download.

- [ ] **Step 1: Write the failing test for ChecksumVerifier**

`backend/src/test/java/com/cubeclient/launcher/download/ChecksumVerifierTest.java`:
```java
package com.cubeclient.launcher.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesSha1ReturnsTrueForCorrectHash() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        // sha1("hello world") = 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed
        assertTrue(ChecksumVerifier.matchesSha1(file, "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
    }

    @Test
    void matchesSha1ReturnsFalseForWrongHash() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        assertFalse(ChecksumVerifier.matchesSha1(file, "0000000000000000000000000000000000000000"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.ChecksumVerifierTest"`
Expected: FAIL — `ChecksumVerifier` does not exist.

- [ ] **Step 3: Implement ChecksumVerifier**

`backend/src/main/java/com/cubeclient/launcher/download/ChecksumVerifier.java`:
```java
package com.cubeclient.launcher.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ChecksumVerifier {
    private ChecksumVerifier() {}

    public static boolean matchesSha1(Path file, String expectedSha1) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            String actual = HexFormat.of().formatHex(hash);
            return actual.equalsIgnoreCase(expectedSha1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.ChecksumVerifierTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 5: Write the failing test for Downloader**

`backend/src/test/java/com/cubeclient/launcher/download/DownloaderTest.java`:
```java
package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloaderTest {

    @TempDir
    Path tempDir;

    static class RecordingFetcher implements HttpFetcher {
        final List<String> downloadedUrls = new ArrayList<>();
        final String contentToWrite;

        RecordingFetcher(String contentToWrite) { this.contentToWrite = contentToWrite; }

        @Override
        public String getString(String url) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            downloadedUrls.add(url);
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, contentToWrite);
        }
    }

    @Test
    void downloadsFileAndVerifiesChecksum() throws IOException {
        // sha1("hello world") = 2aae6c35c94fcfb415dbe95f408b9ce91ee846ed
        RecordingFetcher fetcher = new RecordingFetcher("hello world");
        Downloader downloader = new Downloader(fetcher);
        Path destination = tempDir.resolve("out.jar");

        downloader.downloadVerified("https://example.com/out.jar", destination,
            "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed");

        assertEquals(1, fetcher.downloadedUrls.size());
        assertTrue(Files.exists(destination));
    }

    @Test
    void skipsDownloadIfExistingFileAlreadyMatches() throws IOException {
        Path destination = tempDir.resolve("out.jar");
        Files.writeString(destination, "hello world");
        RecordingFetcher fetcher = new RecordingFetcher("hello world");
        Downloader downloader = new Downloader(fetcher);

        downloader.downloadVerified("https://example.com/out.jar", destination,
            "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed");

        assertEquals(0, fetcher.downloadedUrls.size());
    }

    @Test
    void throwsIfDownloadedFileFailsChecksum() {
        RecordingFetcher fetcher = new RecordingFetcher("wrong content");
        Downloader downloader = new Downloader(fetcher);
        Path destination = tempDir.resolve("out.jar");

        assertThrows(IOException.class, () -> downloader.downloadVerified(
            "https://example.com/out.jar", destination, "2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"));
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.DownloaderTest"`
Expected: FAIL — `Downloader` does not exist.

- [ ] **Step 7: Implement Downloader**

`backend/src/main/java/com/cubeclient/launcher/download/Downloader.java`:
```java
package com.cubeclient.launcher.download;

import com.cubeclient.launcher.http.HttpFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Downloader {
    private final HttpFetcher fetcher;

    public Downloader(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
        if (Files.exists(destination) && ChecksumVerifier.matchesSha1(destination, expectedSha1)) {
            return;
        }
        fetcher.downloadToFile(url, destination);
        if (!ChecksumVerifier.matchesSha1(destination, expectedSha1)) {
            throw new IOException("Checksum mismatch after downloading " + url);
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.DownloaderTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/download backend/src/test/java/com/cubeclient/launcher/download
git commit -m "Add checksum verification and verified downloader"
```

---

## Task 4: Profile storage

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/profile/Profile.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/profile/ProfileStore.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/profile/ProfileStoreTest.java`

**Interfaces:**
- Produces: `Profile(String id, String mcVersion, String loader, List<String> mods)` record. `loader` is one of `"vanilla"`, `"fabric"`, `"legacyfabric"`.
- Produces: `ProfileStore(Path profilesJsonPath)`, `loadAll()` → `List<Profile>` (returns empty list if file doesn't exist), `saveAll(List<Profile> profiles)` → `void`.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/cubeclient/launcher/profile/ProfileStoreTest.java`:
```java
package com.cubeclient.launcher.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadAllReturnsEmptyListWhenFileMissing() throws IOException {
        ProfileStore store = new ProfileStore(tempDir.resolve("profiles.json"));
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void saveAllThenLoadAllRoundTrips() throws IOException {
        Path path = tempDir.resolve("profiles.json");
        ProfileStore store = new ProfileStore(path);
        List<Profile> profiles = List.of(
            new Profile("latest-1.21", "1.21.4", "fabric", List.of("minimap", "fps-hud", "serverlist")),
            new Profile("hypixel-1.8.9", "1.8.9", "legacyfabric", List.of("minimap", "fps-hud", "serverlist"))
        );

        store.saveAll(profiles);
        List<Profile> loaded = store.loadAll();

        assertEquals(profiles, loaded);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.ProfileStoreTest"`
Expected: FAIL — `Profile`/`ProfileStore` do not exist.

- [ ] **Step 3: Implement Profile and ProfileStore**

`backend/src/main/java/com/cubeclient/launcher/profile/Profile.java`:
```java
package com.cubeclient.launcher.profile;

import java.util.List;

public record Profile(String id, String mcVersion, String loader, List<String> mods) {}
```

`backend/src/main/java/com/cubeclient/launcher/profile/ProfileStore.java`:
```java
package com.cubeclient.launcher.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProfileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_LIST_TYPE = new TypeToken<List<Profile>>() {}.getType();

    private final Path profilesJsonPath;

    public ProfileStore(Path profilesJsonPath) {
        this.profilesJsonPath = profilesJsonPath;
    }

    public List<Profile> loadAll() throws IOException {
        if (!Files.exists(profilesJsonPath)) {
            return List.of();
        }
        String json = Files.readString(profilesJsonPath);
        List<Profile> profiles = GSON.fromJson(json, PROFILE_LIST_TYPE);
        return profiles == null ? List.of() : profiles;
    }

    public void saveAll(List<Profile> profiles) throws IOException {
        if (profilesJsonPath.getParent() != null) {
            Files.createDirectories(profilesJsonPath.getParent());
        }
        Files.writeString(profilesJsonPath, GSON.toJson(profiles));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.ProfileStoreTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/profile backend/src/test/java/com/cubeclient/launcher/profile
git commit -m "Add profile storage"
```

---

## Task 5: JSON-lines event emitter and list-profiles CLI command

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/events/EventEmitter.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/Main.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/events/EventEmitterTest.java`

**Interfaces:**
- Consumes: `ProfileStore` (Task 4).
- Produces: `EventEmitter(PrintStream out)`, `progress(String stage, int percent)`, `error(String stage, String message)`, `profiles(List<Profile> profiles)` — each writes one JSON line and flushes.
- Produces: `Main.run(String[] args)` now handles `"list-profiles"` in addition to `"ping"`.

- [ ] **Step 1: Write the failing test for EventEmitter**

`backend/src/test/java/com/cubeclient/launcher/events/EventEmitterTest.java`:
```java
package com.cubeclient.launcher.events;

import com.cubeclient.launcher.profile.Profile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEmitterTest {

    @Test
    void progressWritesOneJsonLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.progress("libraries", 42);

        String line = out.toString().strip();
        assertEquals(1, line.split("\n").length);
        assertTrue(line.contains("\"type\":\"progress\""));
        assertTrue(line.contains("\"stage\":\"libraries\""));
        assertTrue(line.contains("\"percent\":42"));
    }

    @Test
    void errorWritesTypeAndMessage() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.error("auth", "session expired");

        String line = out.toString().strip();
        assertTrue(line.contains("\"type\":\"error\""));
        assertTrue(line.contains("\"stage\":\"auth\""));
        assertTrue(line.contains("\"message\":\"session expired\""));
    }

    @Test
    void profilesWritesProfileArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter emitter = new EventEmitter(new PrintStream(out));

        emitter.profiles(List.of(new Profile("latest-1.21", "1.21.4", "fabric", List.of("minimap"))));

        String line = out.toString().strip();
        assertTrue(line.contains("\"type\":\"profiles\""));
        assertTrue(line.contains("\"mcVersion\":\"1.21.4\""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.EventEmitterTest"`
Expected: FAIL — `EventEmitter` does not exist.

- [ ] **Step 3: Implement EventEmitter**

`backend/src/main/java/com/cubeclient/launcher/events/EventEmitter.java`:
```java
package com.cubeclient.launcher.events;

import com.cubeclient.launcher.profile.Profile;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.PrintStream;
import java.util.List;

public class EventEmitter {
    private static final Gson GSON = new Gson();
    private final PrintStream out;

    public EventEmitter(PrintStream out) {
        this.out = out;
    }

    public void progress(String stage, int percent) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "progress");
        event.addProperty("stage", stage);
        event.addProperty("percent", percent);
        write(event);
    }

    public void error(String stage, String message) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "error");
        event.addProperty("stage", stage);
        event.addProperty("message", message);
        write(event);
    }

    public void profiles(List<Profile> profiles) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "profiles");
        event.add("profiles", GSON.toJsonTree(profiles));
        write(event);
    }

    public void launched() {
        JsonObject event = new JsonObject();
        event.addProperty("type", "launched");
        write(event);
    }

    public void exited(int code) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "exited");
        event.addProperty("code", code);
        write(event);
    }

    private void write(JsonObject event) {
        out.println(GSON.toJson(event));
        out.flush();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.EventEmitterTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 5: Wire `list-profiles` into Main**

Modify `backend/src/main/java/com/cubeclient/launcher/Main.java`:
```java
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
```

- [ ] **Step 6: Test the unchecked-exception safety net**

The `catch (RuntimeException)` in `run` is the only thing standing between an unchecked exception and a crashed backend that Electron cannot interpret. Test it directly by adding this to `backend/src/test/java/com/cubeclient/launcher/MainSmokeTest.java`:

```java
    @Test
    void unknownSubcommandEmitsErrorEventRatherThanCrashing() {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        int exitCode;
        try {
            System.setOut(new PrintStream(captured));
            exitCode = Main.run(new String[] { "no-such-command" });
        } finally {
            System.setOut(original);
        }
        assertEquals(1, exitCode);
        String output = captured.toString();
        assertTrue(output.contains("\"type\":\"error\""));
        assertTrue(output.contains("no-such-command"));
    }
```

This asserts the error path emits a parseable JSON line instead of throwing. (The `RuntimeException` branch itself is exercised end-to-end in Task 7, where `findVersion` throws `IllegalArgumentException` for an unknown Minecraft version.)

Run: `cd backend && ./gradlew test --tests "*.MainSmokeTest"`
Expected: PASS, 2 tests.

- [ ] **Step 7: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests so far pass, output pristine (zero warnings).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/events backend/src/main/java/com/cubeclient/launcher/Main.java backend/src/test/java/com/cubeclient/launcher/events backend/src/test/java/com/cubeclient/launcher/MainSmokeTest.java
git commit -m "Add JSON-lines event emitter and list-profiles command"
```

---

## Task 6: JVM args builder

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/launch/JvmArgsBuilder.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/launch/JvmArgsBuilderTest.java`

**Interfaces:**
- Consumes: `Profile` (Task 4), `VersionDetail` (Task 2).
- Produces: `JvmArgsBuilder.build(Profile profile, VersionDetail detail, Path gameDir, Path sharedRoot, Path javaBin)` → `List<String>` — a full command line: `[javaBin, "-cp", classpath, mainClass, "--username", ..., "--version", ..., "--gameDir", ...]`. Classpath entries are `sharedRoot/libraries/<relativePath>` for each library plus `sharedRoot/versions/<id>/<id>.jar` for the client jar, joined with `File.pathSeparator`.

**Directory layout this encodes** (matches the design spec):
- `gameDir` = `%APPDATA%/CubeClient/instances/<profileId>` — per-profile, isolated (saves, config, mods)
- `sharedRoot` = `%APPDATA%/CubeClient` — holds `libraries/`, `versions/`, `assets/`, shared by every profile

`sharedRoot` is passed in explicitly rather than derived from `gameDir`. Deriving it by path arithmetic (`gameDir.resolveSibling(...)`) silently lands one level too deep — `instances/libraries` instead of `libraries` — and the mistake is invisible because the downloader would make the same wrong turn, so the game still launches with everything filed in the wrong place.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/cubeclient/launcher/launch/JvmArgsBuilderTest.java`:
```java
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
        Path sharedRoot = Path.of("C:", "AppData", "CubeClient");
        Path gameDir = sharedRoot.resolve(Path.of("instances", "latest-1.21"));
        Path javaBin = sharedRoot.resolve(Path.of("runtimes", "17", "bin", "java.exe"));

        List<String> command = new JvmArgsBuilder().build(profile, detail, gameDir, sharedRoot, javaBin);

        assertEquals(javaBin.toString(), command.get(0));
        assertEquals("-cp", command.get(1));
        String classpath = command.get(2);
        List<String> classpathEntries = List.of(classpath.split(java.util.regex.Pattern.quote(File.pathSeparator)));
        assertEquals(2, classpathEntries.size());
        // Shared trees must sit directly under sharedRoot, NOT nested inside instances/.
        assertEquals(
            sharedRoot.resolve(Path.of("libraries", "com", "example", "foo", "1.0", "foo-1.0.jar")).toString(),
            classpathEntries.get(0));
        assertEquals(
            sharedRoot.resolve(Path.of("versions", "1.21.4", "1.21.4.jar")).toString(),
            classpathEntries.get(1));
        assertEquals("net.minecraft.client.main.Main", command.get(3));
        assertTrue(command.contains("--version"));
        assertTrue(command.contains("1.21.4"));

        // --gameDir is the isolated per-profile dir; --assetsDir is the shared tree.
        assertEquals(gameDir.toString(), command.get(command.indexOf("--gameDir") + 1));
        assertEquals(
            sharedRoot.resolve("assets").toString(),
            command.get(command.indexOf("--assetsDir") + 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.JvmArgsBuilderTest"`
Expected: FAIL — `JvmArgsBuilder` does not exist.

- [ ] **Step 3: Implement JvmArgsBuilder**

`backend/src/main/java/com/cubeclient/launcher/launch/JvmArgsBuilder.java`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.JvmArgsBuilderTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/launch/JvmArgsBuilder.java backend/src/test/java/com/cubeclient/launcher/launch/JvmArgsBuilderTest.java
git commit -m "Add JVM args builder"
```

---

## Task 7: Launch orchestration (LaunchCommand)

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/launch/ProcessRunner.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/launch/RealProcessRunner.java`
- Create: `backend/src/main/java/com/cubeclient/launcher/launch/LaunchCommand.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/Main.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/launch/LaunchCommandTest.java`

**Interfaces:**
- Consumes: `VersionManifestFetcher` (Task 2), `Downloader` (Task 3), `Profile`/`ProfileStore` (Task 4), `EventEmitter` (Task 5), `JvmArgsBuilder` (Task 6).
- Produces: `ProcessRunner` interface with `Process start(List<String> command, Path workingDir) throws IOException`.
- Produces: `LaunchCommand(VersionManifestFetcher manifestFetcher, Downloader downloader, JvmArgsBuilder argsBuilder, ProcessRunner processRunner, EventEmitter events)`, `run(Profile profile, Path gameDir, Path sharedRoot, Path javaBin)` → `int` (exit code). Emits `progress` events for `"manifest"`, `"libraries"`, `"client_jar"`, `"launching"`, then `launched()`, then `exited(code)`.
- Produces: `Main.run` now handles `"launch"` (reads profile id from `args[1]`, looks it up via `ProfileStore`, wires real collaborators, calls `LaunchCommand.run`).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/cubeclient/launcher/launch/LaunchCommandTest.java`:
```java
package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.events.EventEmitter;
import com.cubeclient.launcher.http.HttpFetcher;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.manifest.VersionEntry;
import com.cubeclient.launcher.manifest.VersionManifestFetcher;
import com.cubeclient.launcher.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchCommandTest {

    @TempDir
    Path tempDir;

    static class FakeHttpFetcher implements HttpFetcher {
        @Override
        public String getString(String url) {
            String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
            if (url.equals(manifestUrl)) {
                return """
                    { "latest": {"release":"1.21.4","snapshot":"1.21.4"},
                      "versions": [ { "id": "1.21.4", "type": "release", "url": "https://example.com/1.21.4.json" } ] }
                    """;
            }
            return """
                {
                  "id": "1.21.4",
                  "mainClass": "net.minecraft.client.main.Main",
                  "downloads": { "client": { "url": "https://example.com/client.jar", "sha1": "IGNORED", "size": 1 } },
                  "libraries": [
                    {
                      "name": "com.example:foo:1.0",
                      "downloads": {
                        "artifact": {
                          "path": "com/example/foo/1.0/foo-1.0.jar",
                          "url": "https://example.com/foo-1.0.jar",
                          "sha1": "IGNORED",
                          "size": 2
                        }
                      }
                    }
                  ],
                  "assetIndex": { "id": "17", "url": "https://example.com/17.json", "sha1": "IGNORED" }
                }
                """;
        }

        @Override
        public void downloadToFile(String url, Path destination) throws IOException {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, "fake-jar-bytes");
        }
    }

    /**
     * Records every destination it is asked to write to. The recorded paths are the only thing
     * that can catch a regression where downloads are resolved against {@code gameDir} instead of
     * {@code sharedRoot} — such a bug leaves the exit code, the assembled command, and every
     * emitted event completely unchanged, so nothing else in this test would notice it.
     */
    static class NoVerifyDownloader extends Downloader {
        final List<Path> destinations = new ArrayList<>();

        NoVerifyDownloader(HttpFetcher fetcher) { super(fetcher); }

        @Override
        public void downloadVerified(String url, Path destination, String expectedSha1) throws IOException {
            destinations.add(destination);
            if (!Files.exists(destination)) {
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, "fake-jar-bytes");
            }
        }
    }

    static class FakeProcessRunner implements ProcessRunner {
        List<String> lastCommand;

        @Override
        public Process start(List<String> command, Path workingDir) throws IOException {
            this.lastCommand = new ArrayList<>(command);
            // Start a trivial real process so Process#waitFor works cross-platform in the test.
            ProcessBuilder builder = new ProcessBuilder(
                System.getProperty("os.name").toLowerCase().contains("win")
                    ? List.of("cmd", "/c", "exit 0")
                    : List.of("true")
            );
            return builder.start();
        }
    }

    @Test
    void runDownloadsFilesBuildsCommandAndLaunchesProcess() throws IOException {
        FakeHttpFetcher fetcher = new FakeHttpFetcher();
        VersionManifestFetcher manifestFetcher = new VersionManifestFetcher(fetcher);
        NoVerifyDownloader downloader = new NoVerifyDownloader(fetcher);
        FakeProcessRunner processRunner = new FakeProcessRunner();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EventEmitter events = new EventEmitter(new PrintStream(out));

        LaunchCommand launchCommand = new LaunchCommand(
            manifestFetcher, downloader, new JvmArgsBuilder(), processRunner, events);

        Profile profile = new Profile("latest-1.21", "1.21.4", "vanilla", List.of());
        Path sharedRoot = tempDir;
        Path gameDir = sharedRoot.resolve("instances").resolve("latest-1.21");
        Path javaBin = sharedRoot.resolve("runtimes/17/bin/java");

        int exitCode = launchCommand.run(profile, gameDir, sharedRoot, javaBin);

        assertEquals(0, exitCode);
        assertTrue(processRunner.lastCommand.contains("net.minecraft.client.main.Main"));

        // Downloads must land under sharedRoot, NOT under gameDir. Assert the exact paths:
        // this is the only assertion that fails if the sharedRoot/gameDir wiring regresses.
        assertEquals(
            List.of(
                sharedRoot.resolve(Path.of("libraries", "com", "example", "foo", "1.0", "foo-1.0.jar")),
                sharedRoot.resolve(Path.of("versions", "1.21.4", "1.21.4.jar"))
            ),
            downloader.destinations);

        // The classpath the game is launched with must point at those same downloaded files.
        String classpath = processRunner.lastCommand.get(processRunner.lastCommand.indexOf("-cp") + 1);
        for (Path destination : downloader.destinations) {
            assertTrue(classpath.contains(destination.toString()),
                "classpath is missing downloaded file " + destination);
        }

        String eventLog = out.toString();
        assertTrue(eventLog.contains("\"stage\":\"manifest\""));
        assertTrue(eventLog.contains("\"stage\":\"libraries\""));
        assertTrue(eventLog.contains("\"stage\":\"client_jar\""));
        assertTrue(eventLog.contains("\"type\":\"launched\""));
        assertTrue(eventLog.contains("\"type\":\"exited\""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.LaunchCommandTest"`
Expected: FAIL — `ProcessRunner`/`LaunchCommand` do not exist. Also note `Downloader.downloadVerified` is not currently overridable — mark it in Task 3 as-is (it's a plain instance method, not final, so subclassing works).

- [ ] **Step 3: Implement ProcessRunner and RealProcessRunner**

`backend/src/main/java/com/cubeclient/launcher/launch/ProcessRunner.java`:
```java
package com.cubeclient.launcher.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ProcessRunner {
    Process start(List<String> command, Path workingDir) throws IOException;
}
```

`backend/src/main/java/com/cubeclient/launcher/launch/RealProcessRunner.java`:
```java
package com.cubeclient.launcher.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RealProcessRunner implements ProcessRunner {

    /**
     * Starts the game with its merged stdout/stderr redirected straight to a log file.
     *
     * <p>The redirect is not a convenience — it is required for correctness. A subprocess whose
     * output nobody consumes blocks forever once the OS pipe buffer fills (a few dozen KB), and
     * Minecraft plus the Fabric loader emit far more than that during startup alone. Left as an
     * unread pipe, the game freezes and {@code Process#waitFor} never returns. Redirecting to a
     * file hands the draining to the OS, so there is no pipe to fill and no reader thread to
     * manage. It also satisfies the design spec's requirement to persist crash logs for the UI's
     * "show log" affordance.
     */
    @Override
    public Process start(List<String> command, Path workingDir) throws IOException {
        Files.createDirectories(workingDir);
        Path logFile = workingDir.resolve("logs").resolve("latest.log");
        Files.createDirectories(logFile.getParent());
        return new ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
            .start();
    }
}
```

- [ ] **Step 4: Implement LaunchCommand**

`backend/src/main/java/com/cubeclient/launcher/launch/LaunchCommand.java`:
```java
package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.download.Downloader;
import com.cubeclient.launcher.events.EventEmitter;
import com.cubeclient.launcher.manifest.VersionDetail;
import com.cubeclient.launcher.manifest.VersionEntry;
import com.cubeclient.launcher.manifest.VersionManifestFetcher;
import com.cubeclient.launcher.profile.Profile;

import java.io.IOException;
import java.nio.file.Path;

public class LaunchCommand {
    private final VersionManifestFetcher manifestFetcher;
    private final Downloader downloader;
    private final JvmArgsBuilder argsBuilder;
    private final ProcessRunner processRunner;
    private final EventEmitter events;

    public LaunchCommand(
        VersionManifestFetcher manifestFetcher,
        Downloader downloader,
        JvmArgsBuilder argsBuilder,
        ProcessRunner processRunner,
        EventEmitter events
    ) {
        this.manifestFetcher = manifestFetcher;
        this.downloader = downloader;
        this.argsBuilder = argsBuilder;
        this.processRunner = processRunner;
        this.events = events;
    }

    /**
     * @param gameDir    the profile's instance dir, {@code %APPDATA%/CubeClient/instances/<profileId>}
     * @param sharedRoot {@code %APPDATA%/CubeClient}, holding the shared {@code libraries/},
     *                   {@code versions/}, and {@code assets/} trees
     *
     * <p>Downloads MUST land under the same {@code sharedRoot} that {@link JvmArgsBuilder} puts on
     * the classpath. Both take it as an explicit parameter so they cannot disagree.
     */
    public int run(Profile profile, Path gameDir, Path sharedRoot, Path javaBin) throws IOException {
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

        events.progress("client_jar", 60);
        Path clientJar = sharedRoot.resolve(Path.of("versions", detail.id(), detail.id() + ".jar"));
        downloader.downloadVerified(detail.clientDownload().url(), clientJar, detail.clientDownload().sha1());

        events.progress("launching", 90);
        var command = argsBuilder.build(profile, detail, gameDir, sharedRoot, javaBin);
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.LaunchCommandTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 6: Wire `launch` into Main**

Modify `backend/src/main/java/com/cubeclient/launcher/Main.java` — add imports for `Downloader`, `JavaHttpFetcher`, `JvmArgsBuilder`, `LaunchCommand`, `RealProcessRunner`, `VersionManifestFetcher`, `Optional`, and add a case:
```java
            case "launch" -> {
                return runLaunch(args, events);
            }
```
with the new method:
```java
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

            var fetcher = new com.cubeclient.launcher.http.JavaHttpFetcher();
            var manifestFetcher = new com.cubeclient.launcher.manifest.VersionManifestFetcher(fetcher);
            var downloader = new com.cubeclient.launcher.download.Downloader(fetcher);
            var argsBuilder = new com.cubeclient.launcher.launch.JvmArgsBuilder();
            var processRunner = new com.cubeclient.launcher.launch.RealProcessRunner();
            var launchCommand = new com.cubeclient.launcher.launch.LaunchCommand(
                manifestFetcher, downloader, argsBuilder, processRunner, events);

            Path gameDir = appData.resolve("instances").resolve(profile.id());
            String javaMajorVersion = profile.mcVersion().equals("1.8.9") ? "8" : "17";
            Path javaBin = appData.resolve("runtimes").resolve(javaMajorVersion).resolve("bin")
                .resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

            return launchCommand.run(profile, gameDir, appData, javaBin);
        } catch (IOException e) {
            events.error("launch", e.getMessage());
            return 1;
        }
    }
```

(Note: `runtimes/<8|17>/...` JRE auto-download is out of scope for this plan — see "Not Covered" section at the end. For manual end-to-end testing in Task 11, a system Java install is placed at that path manually.)

- [ ] **Step 7: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/launch backend/src/main/java/com/cubeclient/launcher/Main.java backend/src/test/java/com/cubeclient/launcher/launch
git commit -m "Add launch orchestration command"
```

---

## Task 8: Microsoft OAuth device code authentication

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/auth/MicrosoftAuthClient.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/http/HttpFetcher.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/http/JavaHttpFetcher.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/Main.java`
- Test: `backend/src/test/java/com/cubeclient/launcher/auth/MicrosoftAuthClientTest.java`

**Interfaces:**
- Consumes: `HttpFetcher` (Task 2, extended here with a POST method).
- Produces: `HttpFetcher.postJson(String url, String jsonBody, Map<String,String> headers)` → `String`, `throws IOException`.
- Produces: `MicrosoftAuthClient(HttpFetcher fetcher)`, `requestDeviceCode()` → `DeviceCodeResponse(String deviceCode, String userCode, String verificationUri, int expiresIn, int intervalSeconds)`, `pollForMinecraftAuth(DeviceCodeResponse deviceCode)` → `MinecraftAuthResult(String accessToken, String uuid, String username)`, `throws IOException`.
- Produces: `Main.run` now handles `"login"`, printing the user code/verification URI as an event, then polling and emitting the resulting profile info or an auth error.

This flow chains five HTTP calls: device code request → poll `device_code` grant until authorized → Xbox Live (XBL) token → XSTS token → Minecraft Services login-with-xbox → Minecraft profile. To keep this testable without a live Microsoft account, the client is built so each step is a separate private method taking/returning plain data, and the test fakes `HttpFetcher` to return canned responses keyed by URL substring.

- [ ] **Step 1: Extend HttpFetcher with postJson**

Modify `backend/src/main/java/com/cubeclient/launcher/http/HttpFetcher.java`:
```java
package com.cubeclient.launcher.http;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface HttpFetcher {
    String getString(String url) throws IOException;
    String getString(String url, Map<String, String> headers) throws IOException;
    void downloadToFile(String url, Path destination) throws IOException;
    String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException;
}
```

Modify `backend/src/main/java/com/cubeclient/launcher/http/JavaHttpFetcher.java` — add the new method and update the class to implement it:
```java
    @Override
    public String getString(String url, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Deliberately omits the response body: these endpoints are authenticated and their
                // error payloads are not guaranteed to be free of sensitive material.
                throw new IOException("GET " + url + " returned status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    @Override
    public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Status only, never the body: this method carries auth tokens to identity
                // providers, and their error payloads are echoed straight into the user-visible
                // error event. Keep credential material out of it.
                throw new IOException("POST " + url + " returned status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while posting to " + url, e);
        }
    }
```
Add `import java.util.Map;` to the top of the file.

- [ ] **Step 2: Write the failing test for MicrosoftAuthClient**

`backend/src/test/java/com/cubeclient/launcher/auth/MicrosoftAuthClientTest.java`:
```java
package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrosoftAuthClientTest {

    static class ScriptedFetcher implements HttpFetcher {
        String minecraftLoginIdentityToken;
        String profileAuthorizationHeader;

        @Override
        public String getString(String url) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String getString(String url, Map<String, String> headers) {
            if (url.contains("minecraft/profile")) {
                profileAuthorizationHeader = headers.get("Authorization");
                return """
                    { "id": "abc123uuid", "name": "Steve" }
                    """;
            }
            throw new IllegalStateException("Unexpected authenticated GET url: " + url);
        }

        @Override
        public void downloadToFile(String url, Path destination) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
            if (url.contains("devicecode")) {
                return """
                    { "device_code": "DCODE", "user_code": "ABCD-EFGH",
                      "verification_uri": "https://microsoft.com/devicelogin",
                      "expires_in": 900, "interval": 5 }
                    """;
            }
            if (url.contains("/token")) {
                return """
                    { "access_token": "MS_ACCESS_TOKEN", "token_type": "Bearer" }
                    """;
            }
            // Order matters: the XSTS host CONTAINS "xboxlive.com", so it must be matched first
            // or its branch is unreachable and every XSTS call silently gets the XBL response.
            if (url.contains("xsts.auth.xboxlive.com")) {
                return """
                    { "Token": "XSTS_TOKEN", "DisplayClaims": { "xui": [ { "uhs": "XSTS_USER_HASH" } ] } }
                    """;
            }
            if (url.contains("user.auth.xboxlive.com")) {
                return """
                    { "Token": "XBL_TOKEN", "DisplayClaims": { "xui": [ { "uhs": "XBL_USER_HASH" } ] } }
                    """;
            }
            if (url.contains("login_with_xbox")) {
                // Capture the identity token so the test can prove which token/hash pair was used.
                minecraftLoginIdentityToken =
                    JsonParser.parseString(jsonBody).getAsJsonObject().get("identityToken").getAsString();
                return """
                    { "access_token": "MC_ACCESS_TOKEN" }
                    """;
            }
            throw new IllegalStateException("Unexpected POST url: " + url);
        }
    }

    @Test
    void pollForMinecraftAuthChainsAllStepsAndReturnsResult() throws IOException {
        ScriptedFetcher fetcher = new ScriptedFetcher();
        MicrosoftAuthClient client = new MicrosoftAuthClient(fetcher);

        MicrosoftAuthClient.DeviceCodeResponse deviceCode = client.requestDeviceCode();
        assertEquals("ABCD-EFGH", deviceCode.userCode());

        MicrosoftAuthClient.MinecraftAuthResult result = client.pollForMinecraftAuth(deviceCode);

        assertEquals("MC_ACCESS_TOKEN", result.accessToken());
        assertEquals("abc123uuid", result.uuid());
        assertEquals("Steve", result.username());

        // The identity token must be built from the XSTS token and XSTS user hash — NOT the XBL
        // ones. Distinct canned values make a swapped-step regression fail here instead of passing
        // silently.
        assertEquals("XBL3.0 x=XSTS_USER_HASH;XSTS_TOKEN", fetcher.minecraftLoginIdentityToken);

        // The profile must be fetched with an authenticated GET carrying the Minecraft token.
        assertEquals("Bearer MC_ACCESS_TOKEN", fetcher.profileAuthorizationHeader);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*.MicrosoftAuthClientTest"`
Expected: FAIL — `MicrosoftAuthClient` does not exist.

- [ ] **Step 4: Implement MicrosoftAuthClient**

`backend/src/main/java/com/cubeclient/launcher/auth/MicrosoftAuthClient.java`:
```java
package com.cubeclient.launcher.auth;

import com.cubeclient.launcher.http.HttpFetcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;

public class MicrosoftAuthClient {
    private static final String CLIENT_ID = "CUBECLIENT_AZURE_APP_ID"; // replace with real registered app ID
    private static final String DEVICE_CODE_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
        "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpFetcher fetcher;

    public MicrosoftAuthClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public record DeviceCodeResponse(
        String deviceCode, String userCode, String verificationUri, int expiresIn, int intervalSeconds
    ) {}

    public record MinecraftAuthResult(String accessToken, String uuid, String username) {}

    public DeviceCodeResponse requestDeviceCode() throws IOException {
        String body = "{\"client_id\":\"" + CLIENT_ID + "\",\"scope\":\"XboxLive.signin offline_access\"}";
        String response = fetcher.postJson(DEVICE_CODE_URL, body, Map.of());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return new DeviceCodeResponse(
            json.get("device_code").getAsString(),
            json.get("user_code").getAsString(),
            json.get("verification_uri").getAsString(),
            json.get("expires_in").getAsInt(),
            json.get("interval").getAsInt()
        );
    }

    public MinecraftAuthResult pollForMinecraftAuth(DeviceCodeResponse deviceCode) throws IOException {
        String msAccessToken = exchangeDeviceCodeForMicrosoftToken(deviceCode.deviceCode());
        XboxAuth xbl = authenticateWithXboxLive(msAccessToken);
        XboxAuth xsts = authorizeWithXsts(xbl.token());
        String minecraftAccessToken = loginWithXbox(xsts.userHash(), xsts.token());
        return fetchProfileAndBuildResult(minecraftAccessToken);
    }

    private String exchangeDeviceCodeForMicrosoftToken(String deviceCode) throws IOException {
        String body = "{\"client_id\":\"" + CLIENT_ID + "\",\"device_code\":\"" + deviceCode
            + "\",\"grant_type\":\"urn:ietf:params:oauth:grant-type:device_code\"}";
        String response = fetcher.postJson(TOKEN_URL, body, Map.of());
        return JsonParser.parseString(response).getAsJsonObject().get("access_token").getAsString();
    }

    private record XboxAuth(String token, String userHash) {}

    private XboxAuth authenticateWithXboxLive(String msAccessToken) throws IOException {
        String body = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
            + "\"RpsTicket\":\"d=" + msAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\","
            + "\"TokenType\":\"JWT\"}";
        String response = fetcher.postJson(XBL_AUTH_URL, body, Map.of());
        return parseXboxAuth(response);
    }

    private XboxAuth authorizeWithXsts(String xblToken) throws IOException {
        String body = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
            + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        String response = fetcher.postJson(XSTS_AUTH_URL, body, Map.of());
        return parseXboxAuth(response);
    }

    private XboxAuth parseXboxAuth(String response) {
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        String token = json.get("Token").getAsString();
        String userHash = json.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        return new XboxAuth(token, userHash);
    }

    private String loginWithXbox(String userHash, String xstsToken) throws IOException {
        String body = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        String response = fetcher.postJson(MC_LOGIN_URL, body, Map.of());
        return JsonParser.parseString(response).getAsJsonObject().get("access_token").getAsString();
    }

    private MinecraftAuthResult fetchProfileAndBuildResult(String minecraftAccessToken) throws IOException {
        String profileJson = fetchMinecraftProfile(minecraftAccessToken);
        JsonObject json = JsonParser.parseString(profileJson).getAsJsonObject();
        return new MinecraftAuthResult(
            minecraftAccessToken,
            json.get("id").getAsString(),
            json.get("name").getAsString()
        );
    }

    /**
     * The Minecraft Services profile endpoint is a GET with a bearer header — not a POST.
     * Sending POST here returns an error from the live API even when every preceding step
     * succeeded, so this must stay a GET.
     */
    private String fetchMinecraftProfile(String minecraftAccessToken) throws IOException {
        return fetcher.getString(MC_PROFILE_URL, Map.of("Authorization", "Bearer " + minecraftAccessToken));
    }
}
```

Note: `fetchMinecraftProfile` is `private` and needs no test seam, because `HttpFetcher` carries an authenticated-GET overload (`getString(String, Map)`) that the scripted fake implements like any other call. An earlier draft made this a POST through `postJson` with a `protected` override in the test; that hid a real bug, since the live endpoint only accepts GET.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*.MicrosoftAuthClientTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 6: Wire `login` into Main**

Modify `backend/src/main/java/com/cubeclient/launcher/Main.java` — add case:
```java
            case "login" -> {
                return runLogin(events);
            }
```
with:
```java
    private static int runLogin(EventEmitter events) {
        try {
            var fetcher = new com.cubeclient.launcher.http.JavaHttpFetcher();
            var authClient = new com.cubeclient.launcher.auth.MicrosoftAuthClient(fetcher);
            var deviceCode = authClient.requestDeviceCode();
            events.progress("auth_device_code", 0);
            System.out.println("{\"type\":\"device_code\",\"userCode\":\"" + deviceCode.userCode()
                + "\",\"verificationUri\":\"" + deviceCode.verificationUri() + "\"}");
            var result = authClient.pollForMinecraftAuth(deviceCode);
            System.out.println("{\"type\":\"login_success\",\"username\":\"" + result.username()
                + "\",\"uuid\":\"" + result.uuid() + "\"}");
            return 0;
        } catch (IOException e) {
            events.error("login", e.getMessage());
            return 1;
        }
    }
```

- [ ] **Step 7: Run full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/auth backend/src/main/java/com/cubeclient/launcher/http backend/src/main/java/com/cubeclient/launcher/Main.java backend/src/test/java/com/cubeclient/launcher/auth
git commit -m "Add Microsoft OAuth device code authentication"
```

**Before real-world use:** register an Azure AD application (free) to get a real `CLIENT_ID` and replace the `CUBECLIENT_AZURE_APP_ID` placeholder — this is an account/config step for the user to do outside this plan, not something to fake in code.

---

## Task 9: Electron backend process manager

**Files:**
- Create: `ui/src/backendProcess.js`
- Test: `ui/test/backendProcess.test.js`

**Interfaces:**
- Produces: `startBackend(jarPath, subcommand, args, onEvent, spawnFn = require('child_process').spawn)` → returns the spawned child process. Calls `onEvent(parsedJsonObject)` once per JSON line received on stdout; silently ignores lines that aren't valid JSON.

- [ ] **Step 1: Write the failing test**

`ui/test/backendProcess.test.js`:
```js
const { EventEmitter } = require('events');
const { Readable } = require('stream');
const { startBackend } = require('../src/backendProcess');

function makeFakeProcess(stdoutLines) {
  const proc = new EventEmitter();
  proc.stdout = new Readable({
    read() {},
  });
  process.nextTick(() => {
    for (const line of stdoutLines) {
      proc.stdout.push(line + '\n');
    }
    proc.stdout.push(null);
  });
  return proc;
}

test('parses JSON lines from stdout and forwards them to onEvent', (done) => {
  const fakeProcess = makeFakeProcess([
    '{"type":"progress","stage":"manifest","percent":0}',
    '{"type":"launched"}',
  ]);
  const spawnFn = jest.fn(() => fakeProcess);
  const events = [];

  startBackend('/path/to/backend.jar', 'launch', ['latest-1.21'], (event) => {
    events.push(event);
    if (events.length === 2) {
      expect(events[0]).toEqual({ type: 'progress', stage: 'manifest', percent: 0 });
      expect(events[1]).toEqual({ type: 'launched' });
      expect(spawnFn).toHaveBeenCalledWith(
        'java',
        ['-jar', '/path/to/backend.jar', 'launch', 'latest-1.21']
      );
      done();
    }
  }, spawnFn);
});

test('ignores non-JSON lines instead of throwing', (done) => {
  const fakeProcess = makeFakeProcess(['not json', '{"type":"pong"}']);
  const spawnFn = jest.fn(() => fakeProcess);
  const events = [];

  startBackend('/path/to/backend.jar', 'ping', [], (event) => {
    events.push(event);
  }, spawnFn);

  setTimeout(() => {
    expect(events).toEqual([{ type: 'pong' }]);
    done();
  }, 50);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ui && npm test -- backendProcess.test.js`
Expected: FAIL — `../src/backendProcess` module not found.

- [ ] **Step 3: Implement backendProcess.js**

`ui/src/backendProcess.js`:
```js
const readline = require('readline');
const { spawn: defaultSpawn } = require('child_process');

function startBackend(jarPath, subcommand, args, onEvent, spawnFn = defaultSpawn) {
  const proc = spawnFn('java', ['-jar', jarPath, subcommand, ...args]);
  const rl = readline.createInterface({ input: proc.stdout });

  rl.on('line', (line) => {
    let event;
    try {
      event = JSON.parse(line);
    } catch (err) {
      return;
    }
    onEvent(event);
  });

  return proc;
}

module.exports = { startBackend };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ui && npm test -- backendProcess.test.js`
Expected: PASS, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add ui/src/backendProcess.js ui/test/backendProcess.test.js
git commit -m "Add Electron backend process manager"
```

---

## Task 10: Renderer UI — profile list and progress display

**Files:**
- Create: `ui/renderer/renderer.js` (replaces the placeholder from Task 1)
- Create: `ui/jest.config.js`
- Test: `ui/test/renderer.test.js`

**Interfaces:**
- Consumes: nothing from Node/Electron directly — pure DOM functions so they're testable under `jsdom`.
- Produces: `renderProfiles(profiles, container)` — replaces `container`'s content with one `.profile-card` div per profile, each showing `mcVersion` and `loader`, with a `data-profile-id` attribute and a "Play" `<button>`.
- Produces: `renderProgress(stage, percent, container)` — replaces `container`'s content with a text stage label and a `<progress>` element set to `percent`.

- [ ] **Step 1: Configure Jest to use jsdom**

`ui/jest.config.js`:
```js
module.exports = {
  testEnvironment: 'jsdom',
};
```

- [ ] **Step 2: Write the failing test**

`ui/test/renderer.test.js`:
```js
/**
 * @jest-environment jsdom
 */
const { renderProfiles, renderProgress } = require('../renderer/renderer');

test('renderProfiles creates one card per profile with Play button', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProfiles(
    [
      { id: 'latest-1.21', mcVersion: '1.21.4', loader: 'fabric', mods: [] },
      { id: 'hypixel-1.8.9', mcVersion: '1.8.9', loader: 'legacyfabric', mods: [] },
    ],
    container
  );

  const cards = container.querySelectorAll('.profile-card');
  expect(cards.length).toBe(2);
  expect(cards[0].dataset.profileId).toBe('latest-1.21');
  expect(cards[0].textContent).toContain('1.21.4');
  expect(cards[0].querySelector('button').textContent).toBe('Play');
});

test('renderProgress shows stage label and progress bar value', () => {
  document.body.innerHTML = '<div id="container"></div>';
  const container = document.getElementById('container');

  renderProgress('libraries', 42, container);

  expect(container.textContent).toContain('libraries');
  const progressEl = container.querySelector('progress');
  expect(progressEl.value).toBe(42);
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd ui && npm test -- renderer.test.js`
Expected: FAIL — `../renderer/renderer` does not export `renderProfiles`/`renderProgress`.

- [ ] **Step 4: Implement renderer.js**

`ui/renderer/renderer.js`:
```js
function renderProfiles(profiles, container) {
  container.innerHTML = '';
  for (const profile of profiles) {
    const card = document.createElement('div');
    card.className = 'profile-card';
    card.dataset.profileId = profile.id;

    const title = document.createElement('div');
    title.textContent = `${profile.mcVersion} (${profile.loader})`;
    card.appendChild(title);

    const playButton = document.createElement('button');
    playButton.textContent = 'Play';
    card.appendChild(playButton);

    container.appendChild(card);
  }
}

function renderProgress(stage, percent, container) {
  container.innerHTML = '';

  const label = document.createElement('div');
  label.textContent = stage;
  container.appendChild(label);

  const progressEl = document.createElement('progress');
  progressEl.max = 100;
  progressEl.value = percent;
  container.appendChild(progressEl);
}

if (typeof module !== 'undefined') {
  module.exports = { renderProfiles, renderProgress };
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ui && npm test -- renderer.test.js`
Expected: PASS, 2 tests passed.

- [ ] **Step 6: Update package.json test script and index.html script tag**

`ui/package.json` `scripts` section — confirm `"test": "jest"` is present (added in Task 1).

`ui/renderer/index.html` — no change needed; it already loads `renderer.js` and that file guards `module.exports` so it works both under Jest (CommonJS) and in the browser-like Electron renderer (where `module` is undefined unless `nodeIntegration` is set — this project does not enable that, so the guard is required).

- [ ] **Step 7: Commit**

```bash
git add ui/renderer/renderer.js ui/jest.config.js ui/test/renderer.test.js
git commit -m "Add profile list and progress rendering"
```

---

## Task 11: End-to-end wiring and manual verification

**Files:**
- Modify: `ui/main.js`
- Modify: `ui/preload.js`
- Modify: `ui/renderer/index.html`
- Modify: `ui/renderer/renderer.js`

**Interfaces:**
- Consumes: `startBackend` (Task 9), `renderProfiles`/`renderProgress` (Task 10).
- Produces: a working app — on launch, main process calls `startBackend(jarPath, 'list-profiles', [], ...)`, forwards each event to the renderer via `ipcMain`/`ipcRenderer`; renderer calls `renderProfiles` on the `profiles` event; clicking Play calls into main via the preload bridge to run `startBackend(jarPath, 'launch', [profileId], ...)` and renderer calls `renderProgress` on each `progress` event.

This task has no new unit-testable logic beyond what Tasks 9–10 already cover — it is wiring plus a manual end-to-end check, per the design spec's testing strategy ("실제 로그인+실행 전체 흐름은 실기기 수동 테스트로 검증한다").

- [ ] **Step 1: Wire main process IPC**

Modify `ui/main.js`:
```js
const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const { startBackend } = require('./src/backendProcess');

const JAR_PATH = path.join(__dirname, '..', 'backend', 'build', 'libs', 'cubeclient-launcher-backend.jar');

function createWindow() {
  const win = new BrowserWindow({
    width: 1000,
    height: 650,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  win.webContents.once('did-finish-load', () => {
    startBackend(JAR_PATH, 'list-profiles', [], (event) => {
      win.webContents.send('backend-event', event);
    });
  });

  ipcMain.on('launch-profile', (_event, profileId) => {
    startBackend(JAR_PATH, 'launch', [profileId], (event) => {
      win.webContents.send('backend-event', event);
    });
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
```

- [ ] **Step 2: Wire preload bridge**

`ui/preload.js`:
```js
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cubeclient', {
  onBackendEvent: (callback) => ipcRenderer.on('backend-event', (_event, data) => callback(data)),
  launchProfile: (profileId) => ipcRenderer.send('launch-profile', profileId),
});
```

- [ ] **Step 3: Wire renderer to preload bridge**

Modify `ui/renderer/renderer.js` — add at the bottom (still guarded so Jest doesn't execute it, since `window.cubeclient` won't exist under jsdom by default):

```js
if (typeof window !== 'undefined' && window.cubeclient) {
  const app = document.getElementById('app');
  const profilesContainer = document.createElement('div');
  const progressContainer = document.createElement('div');
  app.appendChild(profilesContainer);
  app.appendChild(progressContainer);

  profilesContainer.addEventListener('click', (domEvent) => {
    const card = domEvent.target.closest('.profile-card');
    if (card && domEvent.target.tagName === 'BUTTON') {
      window.cubeclient.launchProfile(card.dataset.profileId);
    }
  });

  window.cubeclient.onBackendEvent((event) => {
    if (event.type === 'profiles') {
      renderProfiles(event.profiles, profilesContainer);
    } else if (event.type === 'progress') {
      renderProgress(event.stage, event.percent, progressContainer);
    } else if (event.type === 'error') {
      progressContainer.textContent = `Error (${event.stage}): ${event.message}`;
    }
  });
}
```

`ui/renderer/index.html` — no change needed.

- [ ] **Step 4: Run all automated tests one more time**

Run: `cd backend && ./gradlew test`
Expected: `BUILD SUCCESSFUL`, all backend tests pass.

Run: `cd ui && npm test`
Expected: all UI tests pass.

- [ ] **Step 5: Manual end-to-end verification**

1. Build the backend jar: `cd backend && ./gradlew jar` → confirm `backend/build/libs/cubeclient-launcher-backend.jar` exists.
2. Create a starter profile file by hand at `%APPDATA%/CubeClient/profiles.json`:
   ```json
   [{ "id": "manual-test-1.21", "mcVersion": "1.21.4", "loader": "vanilla", "mods": [] }]
   ```
3. Place a Java 17 JRE at `%APPDATA%/CubeClient/runtimes/17/bin/java.exe` (copy from any installed JDK 17, or point this path at one for the manual test — automated runtime provisioning is out of scope, see below).
4. Run `cd ui && npm start`. Confirm the window shows one profile card reading "1.21.4 (vanilla)" with a Play button.
5. Click Play. Confirm the progress area updates through stages (`manifest`, `libraries`, `client_jar`, `launching`) and Minecraft 1.21.4 actually launches to the Mojang/Microsoft login screen (a real Microsoft login is out of scope for this task, since `login` isn't wired to `launch` yet — see below).
6. Close the game process and confirm the app doesn't crash.

- [ ] **Step 6: Commit**

```bash
git add ui/main.js ui/preload.js ui/renderer/renderer.js
git commit -m "Wire Electron UI to backend process for end-to-end profile launch"
```

---

## Not Covered By This Plan

Four items that were listed here have since been built (asset downloading, JRE provisioning, login wired into launch, log rotation + a log viewer). What remains:

- **Registering a real Azure AD application.** `MicrosoftAuthClient.CLIENT_ID` is still the placeholder `CUBECLIENT_AZURE_APP_ID`, so `login` cannot succeed against the real Microsoft endpoints. This is an account action the project owner must take (free, at portal.azure.com): register an app, enable the device-code / public-client flow, and paste its application (client) ID in. Until then the launcher runs offline sessions only — singleplayer works, servers reject it.
- **Real device-code polling.** `pollForMinecraftAuth` makes a single pass through the token exchange rather than polling at the interval Microsoft returns until the user finishes signing in, so the first attempt will normally come back with `authorization_pending`. Implement the RFC 8628 poll loop (respect `interval`, `expires_in`, and the `slow_down` response) when wiring up the real client ID.
- **Token refresh.** Only the Minecraft access token is stored; it expires in roughly 24 hours and there is no refresh-token flow, so the user has to sign in again after that.
- **Bootstrapping the backend's own JRE.** The backend now provisions the *game's* runtime, but running the backend jar still needs a Java 17 already present — `CUBECLIENT_JAVA` points at one. Shipping a runtime alongside the app (packaging work) is what actually closes this.
- **Parallel and resumable asset downloads.** Assets are fetched one at a time; a first launch of a modern version pulls a few thousand small files and will be slow. Progress is reported, and already-verified files are skipped on a retry, but there is no concurrency.
- **Legacy (pre-1.7.3) virtual asset layout.** `AssetDownloader` writes the modern hashed `objects/` layout only. 1.8.9 uses that layout, so the supported range is fine, but older versions would need the `virtual/legacy` copy step.
- **`.tar.gz` JRE archives.** `JreProvisioner` unpacks `.zip` only, which covers Windows. Linux and macOS need the tar branch.
- **Sub-projects B and C** (the actual Fabric/Legacy Fabric mod jars for minimap, FPS/resource pack overlay, server list+ping) — separate specs and plans per the design doc.
- **Packaging/installer** (electron-builder config, code signing) — needed before end users install this.
