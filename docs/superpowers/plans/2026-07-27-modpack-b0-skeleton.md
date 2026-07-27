# Sub-project B0: Mod Skeleton and Mod-List Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a new Fabric mod project with a working `클라이언트 설정` entry point on the
title and pause screens, a Deepslate-themed mod-list screen with one real feature (FPS display)
registered in it, and the launcher changes needed to install and configure that mod
automatically.

**Architecture:** `mod/` is a new, independent Gradle project (Fabric Loom) alongside the
existing `backend/` and `ui/` projects — same pattern as this repo already uses, no shared root
build. Inside the mod, pure logic (`Feature`, `FeatureRegistry`, `ModConfig`) has zero Fabric
dependency and is JUnit-tested directly; everything that touches rendering or Minecraft's own
`Screen` classes is Fabric-only and is verified by running the game, the same split this
project already uses between `ui/src/*.js` (tested) and `ui/main.js` (Electron glue, verified by
running the app). The launcher (`backend/`) gains three small, focused changes: it tells the
mod where to store its config via a JVM system property, it fetches the Fabric API jar the same
way it already fetches Fabric Loader libraries, and it copies the mod jar into a Fabric
profile's `mods/` folder before launch.

**Tech Stack:** Java 21 (Minecraft 1.21.4's own requirement), Fabric Loom, Fabric API, JUnit 5.
Existing backend stays Java 17/Gradle Kotlin DSL; the new mod project uses Groovy `build.gradle`
because that is what every Fabric Loom example, the wiki, and the official mod template use —
matching the ecosystem's convention lowers the odds of hitting an undocumented Kotlin-DSL/Loom
interaction.

## Global Constraints

- **Color tokens are the launcher's, reused verbatim** — ground `#0f1216`, panel `#151a20`,
  border `#232932`, text `#e4e8ee`, muted `#8a94a3`, accent `#2fa968`, warning `#e0a23c`. No new
  palette is invented for the mod.
- **The mod never says "프로필"** and never mixes account/version concepts — this constraint
  carried over from the launcher UI applies here too, though B0's screens don't touch account or
  version at all, so it mostly doesn't arise.
- **Settings are shared across every version.** One `mod-config.json` under
  `%APPDATA%/CubeClient`, not one per profile. The mod must not derive its config path from
  `FabricLoader`'s per-instance config directory as its primary source — that directory differs
  per profile (per `instances/<profileId>/`), which is exactly the per-version storage the design
  explicitly rejected.
- **Toggling a feature takes effect immediately**, with no "reopen to apply" step, because the
  screen can be opened mid-game.
- **A card's gear icon is present but disabled in B0** — no feature yet has options to configure.
  Do not build a per-feature settings screen.
- **No mixins.** Everything B0 needs (adding buttons to existing screens, drawing a HUD overlay)
  is reachable through public Fabric API events (`ScreenEvents`, `HudRenderCallback`). Reaching
  for a mixin here would be scope creep this task doesn't need.
- **Unknown ids in a loaded config are ignored, not treated as errors** — a config saved with a
  future version's feature ids (e.g. `"minimap": true` from a later sub-project) must still load
  cleanly today.
- **Version numbers for Loom, Yarn, Fabric Loader, and Fabric API are not hardcoded in this
  plan.** They change on their own release cadence, and pinning a specific build number here
  would likely be stale or wrong by the time this plan runs — this project has already been
  bitten twice by guessed version numbers (Java 8 vs 17, SHA-1 vs SHA-256). Task 1 has the exact
  URLs to check for current values instead.

---

## File Structure

```
mod/                                        NEW project (Fabric Loom)
├─ build.gradle
├─ gradle.properties
├─ settings.gradle
├─ gradlew, gradlew.bat, gradle/wrapper/...
├─ src/main/resources/fabric.mod.json
└─ src/main/java/com/cubeclient/mod/
   ├─ CubeClientMod.java                     ModInitializer — currently empty, registers nothing
   ├─ CubeClientModClient.java                ClientModInitializer — wires everything together
   ├─ registry/
   │  ├─ Category.java                       enum: HUD, CONTROL, WORLD, SERVER
   │  ├─ Feature.java                        interface every toggleable feature implements
   │  └─ FeatureRegistry.java                register/list/filter/search/favorite
   ├─ config/
   │  ├─ ModConfig.java                      the saved data: enabled ids + favorite ids
   │  └─ ConfigStore.java                    load/save ModConfig to/from a JSON file
   ├─ gui/
   │  ├─ Theme.java                          Deepslate colors as int ARGB constants
   │  ├─ FeatureCard.java                    one card widget: icon, name, toggle, heart, gear
   │  ├─ ModListScreen.java                  tabs + search + grid of FeatureCards
   │  └─ ClientSettingsButton.java           injects "클라이언트 설정" into title/pause screens
   └─ features/
      └─ FpsDisplay.java                     the one real Feature this task ships

mod/src/test/java/com/cubeclient/mod/
├─ registry/FeatureRegistryTest.java
└─ config/ConfigStoreTest.java

backend/src/main/java/com/cubeclient/launcher/
├─ launch/JvmArgsBuilder.java                 MODIFY — add -Dcubeclient.configDir
├─ loader/LoaderInstaller.java                MODIFY — also fetch Fabric API
├─ launch/ModDeployer.java                    NEW — copies the mod jar into a profile's mods/
├─ launch/LaunchCommand.java                  MODIFY — calls ModDeployer before launching
└─ Main.java                                  MODIFY — reads an optional mod-jar-path CLI arg

backend/src/test/java/com/cubeclient/launcher/
├─ launch/JvmArgsBuilderTest.java             MODIFY
├─ loader/LoaderInstallerTest.java            MODIFY
└─ launch/ModDeployerTest.java                NEW

ui/main.js                                    MODIFY — computes and passes the mod jar path
```

---

## Task 1: Scaffold the mod project and confirm it builds

This is the highest-risk task in the plan — an external toolchain with version pins that go
stale — so it is isolated first and its only job is: an empty mod that Loom successfully builds
and Minecraft would load.

**Files:**
- Create: `mod/build.gradle`, `mod/gradle.properties`, `mod/settings.gradle`
- Create: `mod/gradle/wrapper/gradle-wrapper.properties` (and the wrapper jar/scripts, via
  `gradle wrapper` — see Step 1)
- Create: `mod/src/main/resources/fabric.mod.json`
- Create: `mod/src/main/java/com/cubeclient/mod/CubeClientMod.java`

**Interfaces:**
- Produces: a `mod/build/libs/cubeclient-mod-<version>.jar` that later tasks (and the backend's
  `ModDeployer`) will reference. The exact jar name comes from `gradle.properties`'
  `mod_version`; pin it to `0.1.0` so the path is predictable: `cubeclient-mod-0.1.0.jar`.

- [ ] **Step 1: Look up current toolchain versions**

There is no bundled local Gradle on this machine (see the launcher's own `backend/gradlew.bat`
bootstrap note in `.superpowers/sdd/progress.md` if you need the same bootstrap trick here).
Before writing any file, fetch these four values and write them down — you will use them in
Step 2:

1. **Fabric Loader version** for Minecraft 1.21.4 — GET
   `https://meta.fabricmc.net/v2/versions/loader/1.21.4` and take the `version` field of the
   first (newest) entry's `loader` object. This is the exact same endpoint and JSON shape
   `backend/.../loader/LoaderInstaller.java`'s `newestLoaderVersion` already parses at runtime —
   you are doing the same lookup, once, by hand.
2. **Yarn mappings build** for 1.21.4 — GET `https://meta.fabricmc.net/v2/versions/yarn/1.21.4`,
   take the `version` field of the first entry (it looks like `1.21.4+build.N`).
3. **Fabric Loom Gradle plugin version** — check
   https://fabricmc.net/develop/ (Fabric's own "get started" generator always shows the current
   recommended Loom version for a chosen Minecraft version) or the Loom releases at
   https://github.com/FabricMC/fabric-loom/releases — pick the newest release that lists 1.21.4
   support.
4. **Fabric API version** for 1.21.4 — check
   https://github.com/FabricTech/fabric-api/releases (or search "fabric api" on Modrinth, filter
   Minecraft version 1.21.4, take the newest release marked compatible) — the version string
   looks like `0.11x.x+1.21.4`.

- [ ] **Step 2: Write `gradle.properties`**

```properties
# Fetched by hand from https://meta.fabricmc.net/v2/versions/loader/1.21.4 and
# https://meta.fabricmc.net/v2/versions/yarn/1.21.4, and from Fabric's own version pages for
# Loom and Fabric API (see Task 1, Step 1 for the exact URLs). Update these together if Minecraft
# 1.21.4 support changes upstream.
minecraft_version=1.21.4
yarn_mappings=<value from Step 1.2>
loader_version=<value from Step 1.1>
fabric_version=<value from Step 1.4>

mod_version=0.1.0
maven_group=com.cubeclient.mod
archives_base_name=cubeclient-mod
```

- [ ] **Step 3: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = 'cubeclient-mod'
```

- [ ] **Step 4: Write `build.gradle`**

```groovy
plugins {
    id 'fabric-loom' version '<Loom version from Step 1.3>'
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
}

loom {
    // No mixins in this mod — every hook it needs is a public Fabric API event, so the
    // default mixin refmap machinery is unused weight. Left at Loom's default (nothing
    // declared) rather than explicitly disabled, since there is nothing to disable.
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"

    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}

java {
    // Minecraft 1.21.4 requires Java 21 to run — this is not a style choice, the game will not
    // start on an older runtime. Matches VersionDetail.javaMajorVersion() for this version on
    // the launcher side.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property "version", project.version
    filteringCharset "UTF-8"
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}
```

- [ ] **Step 5: Write `fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "cubeclient-mod",
  "version": "${version}",
  "name": "CubeClient Mod",
  "description": "The bundled HUD and quality-of-life modpack for the CubeClient launcher.",
  "authors": ["CubeClient"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "main": ["com.cubeclient.mod.CubeClientMod"],
    "client": ["com.cubeclient.mod.CubeClientModClient"]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "1.21.4",
    "java": ">=21",
    "fabric-api": "*"
  }
}
```

`"environment": "client"` is deliberate: nothing in this modpack has server-side logic, and
declaring it client-only means the mod is simply skipped if it ever ends up on a dedicated
server's mods folder instead of erroring.

- [ ] **Step 6: Write an empty `CubeClientMod`**

```java
package com.cubeclient.mod;

import net.fabricmc.api.ModInitializer;

/**
 * Runs on both the client and (hypothetically) a dedicated server. Empty for now — everything
 * this modpack does is client-only rendering and screens, which belongs in
 * {@link CubeClientModClient} instead. Kept as a separate, real entrypoint rather than skipped
 * because {@code fabric.mod.json} already declares it; an empty implementation is clearer than
 * removing the entrypoint and leaving the declaration to point nowhere.
 */
public class CubeClientMod implements ModInitializer {
    @Override
    public void onInitialize() {
    }
}
```

- [ ] **Step 7: Bootstrap the Gradle wrapper and build**

If a system Gradle is available:

```bash
cd mod && gradle wrapper --gradle-version 8.10
```

If not, use whatever bootstrap mechanism `backend/` used (check
`.superpowers/sdd/progress.md` for the exact one-time command that was used there — it names a
downloaded Gradle distribution path).

Then:

```bash
cd mod && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`, and `mod/build/libs/cubeclient-mod-0.1.0.jar` exists.

If dependency resolution fails, the most likely cause is a stale version pin from Step 1 — recheck
the four URLs; Loom, Yarn, and Fabric API all move independently of each other.

- [ ] **Step 8: Commit**

```bash
git add mod/
git commit -m "Scaffold the CubeClient mod project (Fabric Loom, empty entrypoints)"
```

---

## Task 2: Feature, Category, and FeatureRegistry

Pure logic — no Fabric import anywhere in this task. This is what lets it run under plain JUnit
without a Minecraft runtime, the same split the launcher's UI already draws between its pure
`renderer.js` store and Electron-only wiring.

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/registry/Category.java`
- Create: `mod/src/main/java/com/cubeclient/mod/registry/Feature.java`
- Create: `mod/src/main/java/com/cubeclient/mod/registry/FeatureRegistry.java`
- Test: `mod/src/test/java/com/cubeclient/mod/registry/FeatureRegistryTest.java`

**Interfaces:**
- Produces: `Feature` — `String id()`, `String displayName()`, `Category category()`. No
  `onEnable`/`onDisable` yet in this task; Task 7 adds that once there is a real feature that
  needs it, so this interface isn't carrying methods nothing calls yet.
- Produces: `FeatureRegistry.register(Feature)`, `.all()`, `.list(Category filter, String
  searchText, Set<String> favoriteIds)` returning entries ordered favorites-first, then by
  category, then by display name.

- [ ] **Step 1: Write the failing test**

`mod/src/test/java/com/cubeclient/mod/registry/FeatureRegistryTest.java`:

```java
package com.cubeclient.mod.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureRegistryTest {

    record TestFeature(String id, String displayName, Category category) implements Feature {}

    @Test
    void listReturnsEveryRegisteredFeatureWhenUnfiltered() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));

        List<Feature> result = registry.list(null, "", Set.of());

        assertEquals(2, result.size());
    }

    @Test
    void categoryFilterKeepsOnlyThatCategory() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));

        List<Feature> result = registry.list(Category.HUD, "", Set.of());

        assertEquals(List.of("fps"), result.stream().map(Feature::id).toList());
    }

    @Test
    void searchMatchesDisplayNameCaseInsensitively() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));
        registry.register(new TestFeature("cps", "CPS 표시", Category.HUD));

        List<Feature> result = registry.list(null, "fps", Set.of());

        assertEquals(List.of("fps"), result.stream().map(Feature::id).toList());
    }

    // A card's heart button always wins the sort, regardless of category filter or search —
    // otherwise a favorite could vanish from the top of the list just by switching tabs.
    @Test
    void favoritesSortToTheFrontRegardlessOfCategoryOrder() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));

        List<Feature> result = registry.list(null, "", Set.of("zoom"));

        assertEquals(List.of("zoom", "fps"), result.stream().map(Feature::id).toList());
    }

    @Test
    void withinTheSameFavoriteStatusSortsByCategoryThenName() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("zoom", "줌", Category.CONTROL));
        registry.register(new TestFeature("cps", "CPS 표시", Category.HUD));
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));

        List<Feature> result = registry.list(null, "", Set.of());

        // Category enum order (HUD before CONTROL) first, then alphabetical within HUD.
        assertEquals(List.of("cps", "fps", "zoom"), result.stream().map(Feature::id).toList());
    }

    @Test
    void registeringTheSameIdTwiceIsRejected() {
        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new TestFeature("fps", "FPS 표시", Category.HUD));

        assertThrows(IllegalArgumentException.class,
            () -> registry.register(new TestFeature("fps", "다른 이름", Category.HUD)));
    }

    @Test
    void anEmptyRegistryListsNothing() {
        FeatureRegistry registry = new FeatureRegistry();

        assertEquals(List.of(), registry.list(null, "", Set.of()));
    }
}
```

- [ ] **Step 2: Run and watch it fail**

```bash
cd mod && ./gradlew test --tests "*.FeatureRegistryTest"
```

Expected: FAIL — `Category`, `Feature`, `FeatureRegistry` don't exist yet.

- [ ] **Step 3: Write `Category`**

```java
package com.cubeclient.mod.registry;

/**
 * Enum declaration order is the sort order the mod-list screen's category column uses, and also
 * the tab order across the top of the screen (전부 is not a member — it's "no filter", handled
 * by passing {@code null} to {@link FeatureRegistry#list}).
 */
public enum Category {
    HUD("HUD"),
    CONTROL("조작"),
    WORLD("월드"),
    SERVER("서버");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

- [ ] **Step 4: Write `Feature`**

```java
package com.cubeclient.mod.registry;

/**
 * One toggleable entry in the mod-list screen. Implementations are pure metadata plus the
 * category they belong to — the actual on/off behaviour lives wherever the feature hooks into
 * the game (a {@code HudRenderCallback}, a keybinding, etc.), not here. Keeping this interface
 * small is what lets {@link FeatureRegistryTest} test sorting and filtering without touching
 * Minecraft at all.
 */
public interface Feature {
    String id();
    String displayName();
    Category category();
}
```

- [ ] **Step 5: Write `FeatureRegistry`**

```java
package com.cubeclient.mod.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FeatureRegistry {
    private final Map<String, Feature> byId = new LinkedHashMap<>();

    public void register(Feature feature) {
        if (byId.containsKey(feature.id())) {
            throw new IllegalArgumentException("Feature already registered: " + feature.id());
        }
        byId.put(feature.id(), feature);
    }

    public List<Feature> all() {
        return List.copyOf(byId.values());
    }

    /**
     * @param categoryFilter null means "전부" — no category restriction
     * @param searchText     matched against displayName, case-insensitively, substring match
     * @param favoriteIds    ids to sort to the front, ahead of category/name order
     */
    public List<Feature> list(Category categoryFilter, String searchText, Set<String> favoriteIds) {
        String needle = searchText == null ? "" : searchText.toLowerCase(Locale.ROOT);

        List<Feature> result = new ArrayList<>(byId.values());
        result.removeIf(f -> categoryFilter != null && f.category() != categoryFilter);
        result.removeIf(f -> !f.displayName().toLowerCase(Locale.ROOT).contains(needle));

        result.sort(
            Comparator
                .comparing((Feature f) -> !favoriteIds.contains(f.id()))
                .thenComparing(f -> f.category().ordinal())
                .thenComparing(Feature::displayName)
        );
        return result;
    }
}
```

- [ ] **Step 6: Run and watch it pass**

```bash
cd mod && ./gradlew test --tests "*.FeatureRegistryTest"
```

Expected: PASS, 7 tests.

- [ ] **Step 7: Commit**

```bash
git add mod/src/main/java/com/cubeclient/mod/registry mod/src/test/java/com/cubeclient/mod/registry
git commit -m "Add Feature, Category, and FeatureRegistry"
```

---

## Task 3: ModConfig and ConfigStore

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/config/ModConfig.java`
- Create: `mod/src/main/java/com/cubeclient/mod/config/ConfigStore.java`
- Test: `mod/src/test/java/com/cubeclient/mod/config/ConfigStoreTest.java`

**Interfaces:**
- Produces: `ModConfig` — a plain data holder: `Map<String, Boolean> enabled`,
  `Set<String> favorites`. Gson-serializable directly (no custom adapter needed — matches the
  launcher backend's own preference for plain Gson over hand-rolled JSON).
- Produces: `ConfigStore(Path configFile)` — `load() -> ModConfig`, `save(ModConfig)`.
  `load()` returns a fresh empty `ModConfig` if the file doesn't exist, and if it's corrupt it
  moves the bad file to `mod-config.json.bak` and returns a fresh empty one rather than crashing
  — mirrors the launcher's own `game-<timestamp>.log` rotation pattern of never silently
  discarding evidence of a problem.
- Produces: `ConfigStore.resolveConfigDir()` — a static helper reading the
  `cubeclient.configDir` system property, falling back to a caller-supplied default. This is the
  one seam that needs a Fabric-provided fallback path, so it takes the fallback as a parameter
  rather than calling `FabricLoader` itself — keeping this class runnable from plain JUnit. The
  Fabric-side caller (`CubeClientModClient`, wired in Task 7) supplies
  `FabricLoader.getInstance().getConfigDir().resolve("cubeclient")` as that fallback.

- [ ] **Step 1: Write the failing test**

`mod/src/test/java/com/cubeclient/mod/config/ConfigStoreTest.java`:

```java
package com.cubeclient.mod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadingAMissingFileReturnsAnEmptyConfig() throws IOException {
        ConfigStore store = new ConfigStore(tempDir.resolve("mod-config.json"));

        ModConfig loaded = store.load();

        assertTrue(loaded.enabled().isEmpty());
        assertTrue(loaded.favorites().isEmpty());
    }

    @Test
    void savedConfigRoundTripsThroughLoad() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        ModConfig original = new ModConfig(Map.of("fps", true), Set.of("fps"));

        store.save(original);
        ModConfig loaded = store.load();

        assertEquals(Map.of("fps", true), loaded.enabled());
        assertEquals(Set.of("fps"), loaded.favorites());
    }

    // Deleting a feature in a later version must not corrupt the config file for everyone who
    // has it in their enabled map — this is what lets B1-B5 add and remove features freely.
    @Test
    void unknownIdsInTheFileLoadWithoutError() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "some-future-feature": true }, "favorites": [] }
            """);
        ConfigStore store = new ConfigStore(file);

        ModConfig loaded = store.load();

        assertEquals(true, loaded.enabled().get("some-future-feature"));
    }

    @Test
    void aCorruptFileIsMovedAsideRatherThanCrashingOrBeingDeleted() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, "{ not valid json");
        ConfigStore store = new ConfigStore(file);

        ModConfig loaded = store.load();

        assertTrue(loaded.enabled().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("mod-config.json.bak")),
            "the corrupt file should be preserved, not deleted");
        assertEquals("{ not valid json", Files.readString(tempDir.resolve("mod-config.json.bak")));
    }

    @Test
    void savingCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("nested").resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);

        store.save(new ModConfig(Map.of(), Set.of()));

        assertTrue(Files.exists(file));
    }

    @Test
    void resolveConfigDirUsesTheSystemPropertyWhenSet() {
        System.setProperty("cubeclient.configDir", tempDir.toString());
        try {
            Path resolved = ConfigStore.resolveConfigDir(Path.of("unused-fallback"));
            assertEquals(tempDir, resolved);
        } finally {
            System.clearProperty("cubeclient.configDir");
        }
    }

    @Test
    void resolveConfigDirFallsBackWhenThePropertyIsAbsent() {
        System.clearProperty("cubeclient.configDir");

        Path resolved = ConfigStore.resolveConfigDir(tempDir);

        assertEquals(tempDir, resolved);
    }
}
```

- [ ] **Step 2: Run and watch it fail**

```bash
cd mod && ./gradlew test --tests "*.ConfigStoreTest"
```

Expected: FAIL — `ModConfig`/`ConfigStore` don't exist.

- [ ] **Step 3: Write `ModConfig`**

```java
package com.cubeclient.mod.config;

import java.util.Map;
import java.util.Set;

/**
 * The whole of what this mod persists: which feature ids are on, and which are favorited.
 * Deliberately flat and Gson-friendly — no nested objects, no custom (de)serializer needed.
 */
public record ModConfig(Map<String, Boolean> enabled, Set<String> favorites) {
    public static ModConfig empty() {
        return new ModConfig(Map.of(), Set.of());
    }

    public boolean isEnabled(String featureId) {
        return enabled.getOrDefault(featureId, false);
    }
}
```

- [ ] **Step 4: Write `ConfigStore`**

```java
package com.cubeclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;

    public ConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    public ModConfig load() throws IOException {
        if (!Files.exists(configFile)) {
            return ModConfig.empty();
        }
        String json = Files.readString(configFile);
        try {
            ModConfig loaded = GSON.fromJson(json, ModConfig.class);
            return loaded == null ? ModConfig.empty() : loaded;
        } catch (JsonSyntaxException e) {
            // Preserved rather than deleted — mirrors the launcher's game-<timestamp>.log
            // rotation, which never destroys evidence of what went wrong.
            Path backup = configFile.resolveSibling(configFile.getFileName() + ".bak");
            Files.move(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
            return ModConfig.empty();
        }
    }

    public void save(ModConfig config) throws IOException {
        if (configFile.getParent() != null) {
            Files.createDirectories(configFile.getParent());
        }
        Files.writeString(configFile, GSON.toJson(config));
    }

    /**
     * @param fallback used when the launcher did not set {@code -Dcubeclient.configDir} — the
     *                 mod was likely installed by hand into some other launcher. The caller
     *                 (Fabric-side, not this class) supplies Fabric's own per-instance config
     *                 directory as that fallback so this class stays runnable outside a
     *                 Minecraft runtime.
     */
    public static Path resolveConfigDir(Path fallback) {
        String configured = System.getProperty("cubeclient.configDir");
        return configured != null ? Path.of(configured) : fallback;
    }
}
```

- [ ] **Step 5: Add the Gson dependency**

`ModConfig`/`ConfigStore` need Gson, which the launcher backend already depends on but the mod
project does not yet. Add to `mod/build.gradle`'s `dependencies` block:

```groovy
    include implementation("com.google.code.gson:gson:2.11.0")
```

`include` bundles Gson's classes into the mod jar's own namespace-free classpath (Fabric mods
don't get their dependencies merged automatically the way a normal application jar does) — check
Loom's `include` configuration is available for the Loom version pinned in Task 1; if it errors,
use plain `implementation` instead and note that Gson must then already be present at runtime
(Minecraft itself ships Gson, so this is likely fine either way — but `include` is the safer
default for a mod that must not assume the game's own Gson version stays compatible forever).

- [ ] **Step 6: Run and watch it pass**

```bash
cd mod && ./gradlew test --tests "*.ConfigStoreTest"
```

Expected: PASS, 7 tests.

- [ ] **Step 7: Commit**

```bash
git add mod/build.gradle mod/src/main/java/com/cubeclient/mod/config mod/src/test/java/com/cubeclient/mod/config
git commit -m "Add ModConfig and ConfigStore"
```

---

## Task 4: Theme and FeatureCard widget

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/gui/Theme.java`
- Create: `mod/src/main/java/com/cubeclient/mod/gui/FeatureCard.java`

No test file — this task is Fabric rendering code, verified by eye in Task 12, the same split
the launcher UI drew between its jsdom-tested components and the manually-verified frameless
window/drag behaviour.

**Interfaces:**
- Produces: `Theme` — `int` ARGB color constants matching the launcher's hex tokens.
- Produces: `FeatureCard extends ClickableWidget` (or composes one) — constructed with a
  `Feature`, its current enabled/favorite state, and callbacks for toggle-clicked and
  favorite-clicked. Task 5 is the only consumer.

- [ ] **Step 1: Write `Theme`**

```java
package com.cubeclient.mod.gui;

/**
 * The launcher's Deepslate palette (ui/renderer/styles.css), translated to 0xAARRGGBB ints for
 * Minecraft's DrawContext fill/text calls. Values must match the launcher exactly — this is a
 * brand identity, not a separate design.
 */
public final class Theme {
    private Theme() {}

    public static final int GROUND = 0xFF0F1216;
    public static final int PANEL = 0xFF151A20;
    public static final int BORDER = 0xFF232932;
    public static final int TEXT = 0xFFE4E8EE;
    public static final int MUTED = 0xFF8A94A3;
    public static final int ACCENT = 0xFF2FA968;
    public static final int WARNING = 0xFFE0A23C;
}
```

- [ ] **Step 2: Write `FeatureCard`**

```java
package com.cubeclient.mod.gui;

import com.cubeclient.mod.registry.Feature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * One card in the mod-list grid: icon placeholder, name, a toggle button, and a favorite heart.
 * The gear (per-feature settings) is drawn disabled — B0 has no feature with options yet, and a
 * disabled control that says "not yet" is better than hiding the affordance entirely, matching
 * the launcher's own "+ 버전 추가 (준비 중)" pattern.
 */
public class FeatureCard extends ClickableWidget {
    private final Feature feature;
    private boolean enabled;
    private boolean favorite;
    private final Consumer<Feature> onToggle;
    private final Consumer<Feature> onFavoriteToggle;

    public FeatureCard(int x, int y, int width, int height, Feature feature,
                        boolean enabled, boolean favorite,
                        Consumer<Feature> onToggle, Consumer<Feature> onFavoriteToggle) {
        super(x, y, width, height, Text.literal(feature.displayName()));
        this.feature = feature;
        this.enabled = enabled;
        this.favorite = favorite;
        this.onToggle = onToggle;
        this.onFavoriteToggle = onFavoriteToggle;
    }

    public Feature feature() {
        return feature;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(getX(), getY(), getX() + width, getY() + height, Theme.PANEL);
        context.drawBorder(getX(), getY(), width, height, Theme.BORDER);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer, feature.displayName(), getX() + 8, getY() + 8, Theme.TEXT, false);

        // Favorite heart, top-right corner of the card.
        context.drawText(textRenderer, favorite ? "♥" : "♡",
            getX() + width - 16, getY() + 8, favorite ? Theme.ACCENT : Theme.MUTED, false);

        int toggleColor = enabled ? Theme.ACCENT : Theme.BORDER;
        int toggleY = getY() + height - 20;
        context.fill(getX() + 8, toggleY, getX() + width - 8, toggleY + 14, toggleColor);
        context.drawCenteredTextWithShadow(textRenderer, enabled ? "켬" : "끔",
            getX() + width / 2, toggleY + 3, enabled ? Theme.GROUND : Theme.MUTED);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // The favorite heart occupies the top-right ~16px; everything else toggles the feature.
        // A dedicated hit-test rather than a second widget, since the two controls share one
        // card and a nested ClickableWidget inside another fights Minecraft's own widget/mouse
        // dispatch.
        boolean hitHeart = mouseX >= getX() + width - 20 && mouseY <= getY() + 20;
        if (hitHeart) {
            favorite = !favorite;
            onFavoriteToggle.accept(feature);
        } else {
            enabled = !enabled;
            onToggle.accept(feature);
        }
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
```

- [ ] **Step 3: Confirm it compiles**

```bash
cd mod && ./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. (No behaviour to unit-test here — `ClickableWidget` requires a
live `MinecraftClient`; this is verified by eye in Task 12.)

- [ ] **Step 4: Commit**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/Theme.java mod/src/main/java/com/cubeclient/mod/gui/FeatureCard.java
git commit -m "Add the Deepslate theme constants and the FeatureCard widget"
```

---

## Task 5: ModListScreen

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`

**Interfaces:**
- Consumes: `FeatureRegistry`, `ConfigStore`/`ModConfig` from Tasks 2-3; `FeatureCard` from
  Task 4.
- Produces: `ModListScreen(Screen parent, FeatureRegistry registry, ConfigStore configStore)` —
  a `Screen` that can be pushed via `client.setScreen(new ModListScreen(...))` from anywhere,
  which is exactly how Task 6 opens it.

- [ ] **Step 1: Write `ModListScreen`**

```java
package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "클라이언트 설정" screen. Loads the saved config once on open, mutates an in-memory copy
 * as the user toggles/favorites cards, and writes it back on every change rather than only on
 * close — a crash mid-session (or the player just hitting Escape without a dedicated Save
 * button, which this screen doesn't have) must not lose a toggle the player already made.
 */
public class ModListScreen extends Screen {
    private final Screen parent;
    private final FeatureRegistry registry;
    private final ConfigStore configStore;

    private ModConfig config;
    private Category activeCategory; // null = 전부
    private String searchText = "";

    private TextFieldWidget searchField;
    private final List<FeatureCard> cards = new ArrayList<>();

    public ModListScreen(Screen parent, FeatureRegistry registry, ConfigStore configStore) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;
        this.registry = registry;
        this.configStore = configStore;
    }

    @Override
    protected void init() {
        this.config = loadConfigOrEmpty();

        int tabY = 24;
        int tabX = 12;
        for (Category category : Category.values()) {
            Category thisCategory = category;
            addDrawableChild(ButtonWidget.builder(Text.literal(category.displayName()), b -> {
                this.activeCategory = thisCategory;
                rebuildCards();
            }).dimensions(tabX, tabY, 70, 20).build());
            tabX += 74;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("전부"), b -> {
            this.activeCategory = null;
            rebuildCards();
        }).dimensions(tabX, tabY, 70, 20).build());

        searchField = new TextFieldWidget(textRenderer, width - 160, tabY, 148, 20, Text.literal("모드 검색"));
        searchField.setChangedListener(text -> {
            this.searchText = text;
            rebuildCards();
        });
        addDrawableChild(searchField);

        rebuildCards();
    }

    private ModConfig loadConfigOrEmpty() {
        try {
            return configStore.load();
        } catch (IOException e) {
            // A screen cannot surface a launcher-style error event; a broken config is treated
            // as an empty one and the player simply starts from every feature off.
            return ModConfig.empty();
        }
    }

    private void rebuildCards() {
        cards.forEach(this::remove);
        cards.clear();

        List<Feature> visible = registry.list(activeCategory, searchText, config.favorites());

        int columns = 4;
        int cardWidth = 140;
        int cardHeight = 90;
        int gap = 12;
        int startX = 12;
        int startY = 56;

        for (int i = 0; i < visible.size(); i++) {
            Feature feature = visible.get(i);
            int col = i % columns;
            int row = i / columns;
            FeatureCard card = new FeatureCard(
                startX + col * (cardWidth + gap),
                startY + row * (cardHeight + gap),
                cardWidth, cardHeight,
                feature,
                config.isEnabled(feature.id()),
                config.favorites().contains(feature.id()),
                this::onToggle,
                this::onFavoriteToggle
            );
            cards.add(card);
            addDrawableChild(card);
        }
    }

    private void onToggle(Feature feature) {
        Map<String, Boolean> enabled = new HashMap<>(config.enabled());
        enabled.put(feature.id(), !config.isEnabled(feature.id()));
        config = new ModConfig(enabled, config.favorites());
        persist();
    }

    private void onFavoriteToggle(Feature feature) {
        Set<String> favorites = new HashSet<>(config.favorites());
        if (!favorites.remove(feature.id())) {
            favorites.add(feature.id());
        }
        config = new ModConfig(config.enabled(), favorites);
        persist();
        // Favorite order changed, so the grid must re-sort, not just repaint.
        rebuildCards();
    }

    private void persist() {
        try {
            configStore.save(config);
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("설정을 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, Theme.GROUND);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, Theme.TEXT);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
```

- [ ] **Step 2: Confirm it compiles**

```bash
cd mod && ./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java
git commit -m "Add ModListScreen: category tabs, search, favorites, feature grid"
```

---

## Task 6: Inject "클라이언트 설정" into the title and pause screens

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java`

**Interfaces:**
- Consumes: `FeatureRegistry`, `ConfigStore` — passed through to the `ModListScreen` it opens.
- Produces: `ClientSettingsButton.register(FeatureRegistry, ConfigStore)` — called once from
  `CubeClientModClient.onInitializeClient()` in Task 7. Registers the Fabric API screen-init
  listeners; nothing else in the mod calls into this class directly.

- [ ] **Step 1: Write `ClientSettingsButton`**

```java
package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Adds a "클라이언트 설정" button to the title screen and the pause menu, alongside (not
 * replacing) vanilla's own "설정" — the two are intentionally separate, matching the pattern in
 * the Feather Client reference the design was built from.
 *
 * <p>Appended as its own row below the existing buttons rather than positioned beside a specific
 * vanilla button, because vanilla button widths and positions have shifted across Minecraft
 * versions before and a fixed offset calculated against one version's layout is fragile. An
 * appended row survives that.
 */
public final class ClientSettingsButton {
    private ClientSettingsButton() {}

    public static void register(FeatureRegistry registry, ConfigStore configStore) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                addButton(screen, scaledWidth, scaledHeight - 28, registry, configStore);
            } else if (screen instanceof GameMenuScreen) {
                addButton(screen, scaledWidth / 2 - 100, scaledHeight / 4 + 100, registry, configStore);
            }
        });
    }

    private static void addButton(net.minecraft.client.gui.screen.Screen screen, int x, int y,
                                   FeatureRegistry registry, ConfigStore configStore) {
        screen.addDrawableChild(ButtonWidget.builder(Text.literal("클라이언트 설정"), button -> {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            client.setScreen(new ModListScreen(screen, registry, configStore));
        }).dimensions(x, y, 200, 20).build());
    }
}
```

The exact `x`/`y` placement above is a starting guess, not a verified layout — Task 12's manual
pass is where you confirm the button doesn't overlap vanilla's own buttons on both screens and
adjust these two offsets if it does. This is explicitly expected, not a sign something is wrong.

- [ ] **Step 2: Confirm it compiles**

```bash
cd mod && ./gradlew compileJava
```

- [ ] **Step 3: Commit**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java
git commit -m "Add the 클라이언트 설정 button to the title and pause screens"
```

---

## Task 7: FpsDisplay and wiring everything together

This is the task that proves the whole chain: a feature registered, toggleable from the screen
built in Task 5, that actually draws something in the game.

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/FpsDisplay.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java` (create if Task 1 only
  made the stub `CubeClientMod`, not the client entrypoint)

**Interfaces:**
- `FpsDisplay implements Feature` — `id() -> "fps"`, `category() -> Category.HUD`.
- Produces: a `HudRenderCallback` registration that only draws when the feature is enabled in
  the loaded `ModConfig` — checked fresh on every frame via `ConfigStore`, not cached at startup,
  since the whole point of Task 5's screen is toggling this without restarting the game.

- [ ] **Step 1: Write `FpsDisplay`**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The one feature this task ships, chosen because Minecraft already tracks and exposes an FPS
 * counter internally (MinecraftClient.getCurrentFps()) — this task is about proving the
 * registry-to-screen-to-render chain works end to end, not about building a new metric.
 */
public class FpsDisplay implements Feature {
    @Override
    public String id() {
        return "fps";
    }

    @Override
    public String displayName() {
        return "FPS 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getCurrentFps() + " FPS";
        context.drawTextWithShadow(client.textRenderer, text, 4, 4, 0xFFFFFF);
    }
}
```

- [ ] **Step 2: Write `CubeClientModClient`**

```java
package com.cubeclient.mod;

import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.features.FpsDisplay;
import com.cubeclient.mod.gui.ClientSettingsButton;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

public class CubeClientModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path fallback = FabricLoader.getInstance().getConfigDir().resolve("cubeclient");
        Path configFile = ConfigStore.resolveConfigDir(fallback).resolve("mod-config.json");
        ConfigStore configStore = new ConfigStore(configFile);

        FeatureRegistry registry = new FeatureRegistry();
        FpsDisplay fpsDisplay = new FpsDisplay();
        registry.register(fpsDisplay);

        ClientSettingsButton.register(registry, configStore);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            boolean enabled;
            try {
                enabled = configStore.load().isEnabled(fpsDisplay.id());
            } catch (IOException e) {
                enabled = false;
            }
            if (enabled) {
                fpsDisplay.render(context);
            }
        });
    }
}
```

Reading the config file from disk on every rendered frame is wasteful, but B0 is about proving
correctness, not performance, and correctness here means "the screen's toggle and the HUD's
on/off state can never disagree" — a cached-then-invalidated version is a reasonable follow-up
once there is more than one HUD feature reading it (B1's HUD framework task is the natural place
to introduce a shared in-memory config cache all features read from, instead of each one hitting
disk independently the way this task does).

- [ ] **Step 3: Verify the mod builds**

```bash
cd mod && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add mod/src/main/java/com/cubeclient/mod/features mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add FpsDisplay and wire the registry, screen, and HUD render callback together"
```

---

## Task 8: Launcher — tell the mod where its config lives

**Files:**
- Modify: `backend/src/main/java/com/cubeclient/launcher/launch/JvmArgsBuilder.java`
- Modify: `backend/src/test/java/com/cubeclient/launcher/launch/JvmArgsBuilderTest.java`

**Interfaces:**
- No signature change — `JvmArgsBuilder.build(...)`'s existing `sharedRoot` parameter is what
  gets passed as the property's value, so nothing new is threaded through.

- [ ] **Step 1: Write the failing test**

Add to `JvmArgsBuilderTest.java` (open the file first to match its existing fixture setup —
`detail`, `gameDir`, `sharedRoot`, `javaBin`, a `Session`, and `InstalledLoader.none()` are
already built by the existing tests in this file; reuse those rather than re-declaring them):

```java
    // The mod resolves its own config location from this property; without it every profile
    // would fall back to Fabric's per-instance config dir, which is exactly the per-version
    // storage the design rejected in favour of one shared file.
    @Test
    void setsTheModConfigDirectoryToTheSharedRoot() {
        List<String> command = new JvmArgsBuilder().build(
            profile, detail, gameDir, sharedRoot, javaBin, Session.offline(profile.id()),
            InstalledLoader.none());

        assertTrue(command.contains("-Dcubeclient.configDir=" + sharedRoot),
            "actual: " + command);
    }
```

- [ ] **Step 2: Run and watch it fail**

```bash
cd backend && ./gradlew test --tests "*.JvmArgsBuilderTest"
```

Expected: FAIL — the assertion finds no such argument.

- [ ] **Step 3: Add the argument**

In `JvmArgsBuilder.build`, immediately after `command.add(javaBin.toString());`:

```java
        command.add(javaBin.toString());
        // Told to the mod so mod-config.json lives at the shared %APPDATA%/CubeClient root and
        // is the same file no matter which profile (version) is launched.
        command.add("-Dcubeclient.configDir=" + sharedRoot);
        command.add("-cp");
```

- [ ] **Step 4: Run and watch it pass**

```bash
cd backend && ./gradlew test --tests "*.JvmArgsBuilderTest"
```

Expected: PASS, all existing tests plus the new one.

- [ ] **Step 5: Run the whole backend suite**

```bash
cd backend && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`, zero warnings.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/launch/JvmArgsBuilder.java backend/src/test/java/com/cubeclient/launcher/launch/JvmArgsBuilderTest.java
git commit -m "Pass the mod its shared config directory via a JVM system property"
```

---

## Task 9: Launcher — fetch Fabric API alongside Fabric Loader

**Files:**
- Modify: `backend/src/main/java/com/cubeclient/launcher/loader/LoaderInstaller.java`
- Modify: `backend/src/test/java/com/cubeclient/launcher/loader/LoaderInstallerTest.java`

**Interfaces:**
- No new public method — Fabric API is added to the same `InstalledLoader.extraClasspath()`
  list that loader libraries already populate, so every existing caller (`JvmArgsBuilder`,
  `LaunchCommand`) needs no change at all.

**A version has to be picked here, same caveat as Task 1's Step 1.4**: this class cannot look
Fabric API's version up from `meta.fabricmc.net` the way it already does for the loader itself —
Fabric API isn't part of that API surface. Use a `private static final String` constant, pinned
to the **same version you resolved in Task 1** (the mod project and the launcher must agree on
which Fabric API build ships, since the mod is compiled against it and the launcher is what puts
it on the running game's classpath).

- [ ] **Step 1: Write the failing test**

Add to `LoaderInstallerTest.java`, reusing the existing `FakeFetcher`/`RecordingDownloader`
fixtures already in the file:

```java
    // Fabric API is a separate distribution from Fabric Loader, but ScreenEvents and
    // HudRenderCallback — everything the CubeClient mod needs to add a settings button and draw
    // a HUD — live in Fabric API, not the loader. Every Fabric profile needs it, unconditionally.
    @Test
    void alsoInstallsFabricApiForEveryFabricProfile() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        LoaderInstaller.InstalledLoader result =
            installer(fetcher, downloader).install("fabric", "1.21.4", tempDir);

        assertTrue(downloader.urls.stream().anyMatch(u -> u.contains("fabric-api")),
            "actual: " + downloader.urls);
        assertTrue(result.extraClasspath().stream()
            .anyMatch(p -> p.toString().contains("fabric-api")),
            "actual: " + result.extraClasspath());
    }

    @Test
    void vanillaDoesNotInstallFabricApiEither() throws IOException {
        FakeFetcher fetcher = new FakeFetcher();
        RecordingDownloader downloader = new RecordingDownloader(fetcher);

        installer(fetcher, downloader).install("vanilla", "1.21.4", tempDir);

        assertTrue(downloader.urls.isEmpty());
    }
```

- [ ] **Step 2: Run and watch it fail**

```bash
cd backend && ./gradlew test --tests "*.LoaderInstallerTest"
```

Expected: FAIL — no URL contains `fabric-api`.

- [ ] **Step 3: Add the Fabric API download**

In `LoaderInstaller.java`, add the version constant near `FABRIC_META`:

```java
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";

    // Fabric API has no meta.fabricmc.net version-list endpoint the way the loader and Yarn
    // mappings do, so this cannot be resolved dynamically the way newestLoaderVersion() resolves
    // the loader version. Pin it to the same build the mod project itself compiles against (see
    // mod/gradle.properties' fabric_version, set in this plan's Task 1) — the two must agree,
    // since the mod is compiled against this jar and this is what puts it on the classpath at
    // launch.
    private static final String FABRIC_API_VERSION = "<same value as mod/gradle.properties fabric_version>";
    private static final String FABRIC_API_COORDINATE =
        "net.fabricmc.fabric-api:fabric-api:" + FABRIC_API_VERSION;
```

Then in `install`, after the `libraries` loop and before `return new InstalledLoader(...)`:

```java
        // Fabric API is a separate distribution from the loader itself, published to the same
        // Maven host, so it reuses downloadLibrary rather than introducing a second HTTP
        // mechanism. Every Fabric profile needs it — CubeClient's own mod depends on the
        // ScreenEvents and HudRenderCallback APIs it provides.
        classpath.add(downloadLibrary(FABRIC_API_COORDINATE, metaHost.replace("/v2", ""), sharedRoot));
```

`metaHost.replace("/v2", "")` turns `https://meta.fabricmc.net/v2` back into
`https://meta.fabricmc.net`, which is the Maven root Fabric API is actually served from — reusing
`metaHost` rather than adding a second constant, since both loader libraries and Fabric API come
from the same `maven.fabricmc.net` host in practice (this is the same host
`downloadLibrary`'s existing calls already resolve against for loader library coordinates whose
`url` field points there).

- [ ] **Step 4: Run and watch it pass**

```bash
cd backend && ./gradlew test --tests "*.LoaderInstallerTest"
```

Expected: PASS, all existing tests plus the two new ones (verify none of the earlier assertions
about "exactly 2 extraClasspath entries" broke — `putsEveryDownloadedLoaderLibraryOnTheClasspath`
asserted `assertEquals(2, result.extraClasspath().size())`; it will now need `3`. Update that
assertion rather than leaving it failing.)

- [ ] **Step 5: Run the whole backend suite**

```bash
cd backend && ./gradlew test
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/loader/LoaderInstaller.java backend/src/test/java/com/cubeclient/launcher/loader/LoaderInstallerTest.java
git commit -m "Install Fabric API alongside Fabric Loader for every Fabric profile"
```

---

## Task 10: Launcher — deploy the mod jar into a profile's mods folder

**Files:**
- Create: `backend/src/main/java/com/cubeclient/launcher/launch/ModDeployer.java`
- Create: `backend/src/test/java/com/cubeclient/launcher/launch/ModDeployerTest.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/launch/LaunchCommand.java`
- Modify: `backend/src/main/java/com/cubeclient/launcher/Main.java`

**Interfaces:**
- Produces: `ModDeployer.deploy(Path sourceJar, Path gameDir) throws IOException` — copies
  `sourceJar` into `<gameDir>/mods/<sourceJar's file name>`, skipping the copy if a file already
  there has an identical SHA-256 (same pattern the launcher already uses for network downloads,
  applied to a local-to-local copy instead).
- Modifies: `LaunchCommand.run(...)` gains one new parameter, `Path modJarSource` (nullable).
  When non-null and `profile.loader().equals("fabric")`, it's deployed as a new stage between
  `"loader"` and `"runtime"`.
- Modifies: `Main.runLaunch` parses an optional third CLI argument as the mod jar path.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/cubeclient/launcher/launch/ModDeployerTest.java`:

```java
package com.cubeclient.launcher.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDeployerTest {

    @TempDir
    Path tempDir;

    @Test
    void copiesTheJarIntoTheGameDirsModsFolder() throws IOException {
        Path source = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(source, "jar-bytes");
        Path gameDir = tempDir.resolve("instance");

        new ModDeployer().deploy(source, gameDir);

        Path deployed = gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar");
        assertTrue(Files.exists(deployed));
        assertEquals("jar-bytes", Files.readString(deployed));
    }

    // A profile is relaunched far more often than the mod jar changes, and re-copying tens of
    // kilobytes on every launch is wasted work the checksum comparison avoids — same reasoning
    // the network downloader already applies to Mojang's assets.
    @Test
    void skipsTheCopyWhenAnIdenticalJarIsAlreadyDeployed() throws IOException {
        Path source = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(source, "jar-bytes");
        Path gameDir = tempDir.resolve("instance");
        Path deployed = gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar");
        Files.createDirectories(deployed.getParent());
        Files.writeString(deployed, "jar-bytes");
        long originalModifiedTime = Files.getLastModifiedTime(deployed).toMillis();

        Thread.sleep(10);
        new ModDeployer().deploy(source, gameDir);

        assertEquals(originalModifiedTime, Files.getLastModifiedTime(deployed).toMillis(),
            "an identical file must not be rewritten");
    }

    @Test
    void replacesAnOutdatedJarWithADifferentOne() throws IOException {
        Path source = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(source, "new-bytes");
        Path gameDir = tempDir.resolve("instance");
        Path deployed = gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar");
        Files.createDirectories(deployed.getParent());
        Files.writeString(deployed, "old-bytes");

        new ModDeployer().deploy(source, gameDir);

        assertEquals("new-bytes", Files.readString(deployed));
    }
}
```

- [ ] **Step 2: Run and watch it fail**

```bash
cd backend && ./gradlew test --tests "*.ModDeployerTest"
```

Expected: FAIL — `ModDeployer` doesn't exist.

- [ ] **Step 3: Write `ModDeployer`**

```java
package com.cubeclient.launcher.launch;

import com.cubeclient.launcher.download.ChecksumVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Puts the CubeClient mod jar into a profile's mods/ folder, the local-file counterpart to how
 * {@link com.cubeclient.launcher.loader.LoaderInstaller} fetches loader libraries over the
 * network — same "skip if the checksum already matches" idea, applied to a copy instead of a
 * download.
 */
public class ModDeployer {

    public void deploy(Path sourceJar, Path gameDir) throws IOException {
        Path destination = gameDir.resolve("mods").resolve(sourceJar.getFileName());

        if (Files.exists(destination) && ChecksumVerifier.matchesSha256(destination, sha256Of(sourceJar))) {
            return;
        }

        Files.createDirectories(destination.getParent());
        Files.copy(sourceJar, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private String sha256Of(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
```

- [ ] **Step 4: Run and watch it pass**

```bash
cd backend && ./gradlew test --tests "*.ModDeployerTest"
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Wire it into `LaunchCommand`**

Add a field, constructor parameter, and call. In `LaunchCommand.java`:

```java
    private final ModDeployer modDeployer;
```

Add to the constructor parameter list and assignment (alongside the existing fields — keep
constructor parameter order consistent with the field declaration order, matching the existing
style in this class).

In `run(...)`, add a new parameter `Path modJarSource` at the end of the signature, and insert
this block between the `"loader"` progress stage and the `"runtime"` stage:

```java
        events.progress("loader", 85);
        var loader = loaderInstaller.install(profile.loader(), profile.mcVersion(), sharedRoot);

        // Only Fabric profiles can load a mod at all, and only when the launcher told us where
        // the mod jar is (it may not have been built yet in a dev checkout — that must not
        // block launching vanilla or Fabric-without-the-modpack).
        if (modJarSource != null && "fabric".equals(profile.loader())) {
            modDeployer.deploy(modJarSource, gameDir);
        }

        events.progress("runtime", 88);
```

- [ ] **Step 6: Update `LaunchCommandTest`**

Every existing call to `launchCommand.run(...)` gains a new trailing argument. Open
`backend/src/test/java/com/cubeclient/launcher/launch/LaunchCommandTest.java` and add `null` as
the new final argument to each call site — `null` is the correct value for tests that aren't
about mod deployment, since it means "no mod jar available," the same as a fresh dev checkout
where `mod/` hasn't been built yet.

Add one new test proving the wiring itself (not `ModDeployer`'s own behaviour, which
`ModDeployerTest` already covers — this test only proves `LaunchCommand` calls it under the
right condition):

```java
    // Only proves LaunchCommand calls ModDeployer when it should — ModDeployer's own copy/skip
    // behaviour is ModDeployerTest's job, not this one's.
    @Test
    void deploysTheModJarForAFabricProfileWhenOneIsGiven() throws IOException {
        Path modJar = tempDir.resolve("cubeclient-mod-0.1.0.jar");
        Files.writeString(modJar, "jar-bytes");

        // ... build launchCommand with the profile's loader set to "fabric", as the existing
        // fabric-profile tests in this file already do, then:
        launchCommand.run(profile, gameDir, sharedRoot, "windows", session, modJar);

        assertTrue(Files.exists(gameDir.resolve("mods").resolve("cubeclient-mod-0.1.0.jar")));
    }

    @Test
    void aNullModJarSourceLaunchesWithoutDeployingAnything() throws IOException {
        // ... same fabric-profile setup, but:
        int exitCode = launchCommand.run(profile, gameDir, sharedRoot, "windows", session, null);

        assertEquals(0, exitCode);
        assertFalse(Files.exists(gameDir.resolve("mods")));
    }
```

(Written against the existing file's own fixture-building style — read
`LaunchCommandTest.java` first and match its exact setup pattern for `profile`/`gameDir`/
`sharedRoot`/`session` rather than re-declaring them, the same instruction as Task 8's test.)

- [ ] **Step 7: Wire it into `Main.runLaunch`**

```java
    private static int runLaunch(String[] args, EventEmitter events) {
        if (args.length < 2) {
            events.error("cli", "launch requires a profile id argument");
            return 1;
        }
        String profileId = args[1];
        // Optional: the path to the built CubeClient mod jar, supplied by the launcher UI when
        // it exists. Absent in a dev checkout where mod/ hasn't been built yet, or if a profile
        // is launched some other way — either is fine, it just means no modpack this run.
        Path modJarSource = args.length >= 3 ? Path.of(args[2]) : null;
        try {
```

...and pass `modJarSource` as the new trailing argument to `launchCommand.run(...)`.

Also update the `LaunchCommand` construction a few lines above to pass `new ModDeployer()` for
the new constructor parameter.

- [ ] **Step 8: Run the whole backend suite**

```bash
cd backend && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`, zero warnings, every test passing.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/cubeclient/launcher/launch/ModDeployer.java backend/src/test/java/com/cubeclient/launcher/launch/ModDeployerTest.java backend/src/main/java/com/cubeclient/launcher/launch/LaunchCommand.java backend/src/test/java/com/cubeclient/launcher/launch/LaunchCommandTest.java backend/src/main/java/com/cubeclient/launcher/Main.java
git commit -m "Deploy the CubeClient mod jar into a Fabric profile's mods folder before launch"
```

---

## Task 11: UI — pass the mod jar path when launching

**Files:**
- Modify: `ui/main.js`

No test file — this is Electron main-process glue, the same category `ui/main.js`'s existing
`JAR_PATH`/`javaCommand` computation already falls into (verified by running the app, not by
Jest).

- [ ] **Step 1: Compute the mod jar path**

Near the existing `JAR_PATH` constant at the top of `main.js`:

```js
const MOD_JAR_PATH = path.join(
  __dirname,
  '..',
  'mod',
  'build',
  'libs',
  'cubeclient-mod-0.1.0.jar'
);
```

The version number in the filename must match `mod/gradle.properties`' `mod_version` from
Task 1 — if that value changes later, this constant has to change with it. (A follow-up beyond
B0 would read this from a manifest instead of hardcoding the version twice; not worth it for one
call site yet.)

- [ ] **Step 2: Pass it in `launch-profile`**

```js
  ipcMain.on('launch-profile', (_event, profileId) => {
    if (typeof profileId !== 'string') return;
    const session = authStore.load();
    if (!session) {
      send({
        type: 'error',
        stage: 'launch',
        message: 'Microsoft 계정으로 로그인한 뒤에 실행할 수 있습니다.',
      });
      return;
    }

    // Absent in a dev checkout where mod/ hasn't been built yet — the backend already treats a
    // missing third argument as "no modpack this run" rather than an error.
    const launchArgs = fs.existsSync(MOD_JAR_PATH) ? [profileId, MOD_JAR_PATH] : [profileId];
    startBackend(JAR_PATH, 'launch', launchArgs, send, undefined, javaCommand, session);
  });
```

`fs` is already imported in `main.js` from the earlier debug-logging work; if this plan is
executed on a checkout from before that change, add `const fs = require('fs');` near the top
alongside the existing `path` import.

- [ ] **Step 3: Verify by running the app**

Build both jars first:

```bash
cd backend && ./gradlew jar
cd ../mod && ./gradlew build
```

Then run the launcher (see Task 12 for the exact command and the full manual checklist — this
step is just a syntax/wiring sanity check, not the full pass):

```bash
cd ui && node --check main.js
```

Expected: no output (valid syntax).

- [ ] **Step 4: Commit**

```bash
git add ui/main.js
git commit -m "Pass the built mod jar path when launching a Fabric profile"
```

---

## Task 12: Manual verification

**Files:** none — this task changes no code.

Screen rendering, button placement, and toggle behaviour cannot be caught by any test in this
plan — Sub-project A's own history is full of bugs that only a real run surfaced. This task is
where that happens for B0.

- [ ] **Step 1: Build everything**

```bash
cd backend && ./gradlew jar
cd ../mod && ./gradlew build
```

Confirm both `backend/build/libs/cubeclient-launcher-backend.jar` and
`mod/build/libs/cubeclient-mod-0.1.0.jar` exist.

- [ ] **Step 2: Start the launcher and launch the Fabric profile**

```bash
cd ui && CUBECLIENT_JAVA="C:/Users/Skdji/devtools/jdk17/jdk-17.0.19+10/bin/java.exe" npx electron .
```

Sign in, select the Fabric 1.21.4 profile, press PLAY.

- [ ] **Step 3: Confirm the mod loaded**

In the game's `latest.log` (opened via the launcher's own `로그 → 열기` button, or directly from
`%APPDATA%/CubeClient/instances/<fabric profile id>/logs/latest.log`), confirm a line naming
`cubeclient-mod` among the loaded mods, and no Fabric API resolution errors.

- [ ] **Step 4: Walk the title screen**

1. `클라이언트 설정` button is visible and does not overlap any vanilla button.
2. Pressing it opens the mod-list screen: dark Deepslate background, category tabs, search box,
   at least one card (FPS 표시).
3. Escape (or the screen's own close behaviour) returns cleanly to the title screen — no crash,
   no stuck black screen.

- [ ] **Step 5: Walk the pause screen (ESC in-game)**

1. `클라이언트 설정` sits alongside vanilla `설정`, doesn't overlap `게임으로`/`저장하고
   나가기`.
2. Opens the same mod-list screen.

- [ ] **Step 6: Toggle FPS**

1. Open the mod-list screen, press the FPS card's toggle. It flips to `켬` immediately.
2. Close the screen. FPS text appears in the top-left corner of the game view within a moment.
3. Reopen the screen, toggle it off. FPS text disappears.
4. Toggle it back on, then fully quit and relaunch the game (or just the profile). Reopen the
   mod-list screen — FPS should still read `켬`, proving `mod-config.json` round-tripped through
   a real restart, not just an in-memory session.

- [ ] **Step 7: Confirm search and favorites**

1. Type `fps` in the search box — the FPS card is the only one shown, others (once B1+ adds
   them — for B0 there may be nothing else to filter out, note that in the report instead of
   treating it as unverifiable) are hidden.
2. Clear the search. Press the FPS card's heart. It moves to (or stays at) the front of the
   grid.

- [ ] **Step 8: Confirm the vanilla profile is unaffected**

Launch the vanilla 1.21.4 profile. Confirm no `클라이언트 설정` button appears anywhere (no
Fabric, no mod, by design) and the game behaves exactly as it did before this plan.

- [ ] **Step 9: Report findings**

This task produces no commit. Report which of the checks above passed, and for anything that
didn't — especially button overlap on either screen — note it as a known issue for a quick
follow-up fix rather than blocking the rest of Sub-project B on it.

---

## Self-review notes

Checked against `docs/superpowers/specs/2026-07-27-modpack-b0-skeleton-design.md`:

- Deepslate palette reused verbatim — `Theme.java` in Task 4, values copied from the launcher's
  own `styles.css` tokens.
- Title + pause screen entry points, kept separate from vanilla `설정` — Task 6.
- Category tabs, search, favorites, disabled gear — Task 5, Task 4.
- Shared (not per-version) config, via the JVM system property mechanism the spec specifies —
  Task 3 (`ConfigStore.resolveConfigDir`) and Task 8 (the launcher side that sets it).
- Immediate toggle effect, no reopen-to-apply — Task 5's `persist()` runs on every card click,
  and Task 7's `HudRenderCallback` re-reads the config every frame rather than caching it at
  startup.
- Unknown ids ignored on load, not treated as corruption — Task 3's
  `ConfigStoreTest.unknownIdsInTheFileLoadWithoutError`.
- Launcher auto-installs the mod and Fabric API — Tasks 9 and 10.
- Out-of-scope items from the spec (per-feature settings screens via the gear, keybinding
  changes, New/Hypixel/PvP tabs) are not built — the gear renders but has no click handler wired
  in `FeatureCard`, matching "always disabled" from the spec.

Fixed during review: the design spec's mention of Modrinth/CurseForge for Fabric API sourcing
was already corrected in the spec itself before this plan was written (see the spec's own
self-review note); this plan follows that correction — Task 9 fetches Fabric API from the same
Fabric Maven host the loader libraries already come from.

Known gap carried forward from the design: GPU usage, per-feature settings via the gear icon,
keybinding remapping, and the `New`/`Hypixel`/`PvP` tabs are explicitly out of scope for B0 and
are not addressed by any task here — they belong to B1 onward per the spec's own roadmap.

New gap this plan introduces and accepts: `FABRIC_API_VERSION` in `LoaderInstaller` (Task 9) is
a hardcoded constant with no live lookup, unlike the Fabric Loader version it sits next to. This
is a deliberate, spec-acknowledged limitation (Fabric API has no `meta.fabricmc.net` endpoint to
query) rather than an oversight — flagged explicitly in Task 9 rather than silently mismatched
against the mod project's own `fabric_version`.
