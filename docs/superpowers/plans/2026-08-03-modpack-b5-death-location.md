# Sub-project B5: 죽은 위치 표시 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 플레이어가 죽으면 그 자리를 기억해서, 실제 3D 월드에 반투명 빔으로, B4 미니맵 위엔 점으로 동시에 표시한다. 여러 번 죽으면 전부 동시에 남고, 사용자가 모드 목록 화면에서 "전체 삭제"를 누르기 전까진 계속 유지된다. 월드/서버별로 구분해서 저장한다.

**Architecture:** `com.cubeclient.mod.death` 패키지에 순수 로직(값 타입, 필터, 죽음 edge 검출)과 저장소(`DeathLocationStore`, JSON 파일)를 분리한다. `DeathLocationDisplay`(새 `Feature`)가 죽음 감지와 3D 빔 렌더링을 담당하고, `TerrainMinimap`(B4)이 같은 저장소를 읽어 미니맵에 점을 추가로 찍는다 — 두 기능이 서로 의존하지 않고 `DeathLocationStore` 하나를 공유한다. 3D 빔은 바닐라 비콘이 실제로 쓰는 `BeaconBlockEntityRenderer.renderBeam(...)`(public static 메서드)을 그대로 재사용한다 — 이 프로젝트 최초의 3D 월드 렌더링이지만, 정점을 직접 그리지 않고 검증된 바닐라 구현을 호출하는 것이라 리스크가 크게 줄어든다.

**Tech Stack:** Fabric Loom 1.10.2, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader `0.19.3`, Fabric API `0.119.4+1.21.4`(`fabric-rendering-v1` 포함), JDK 21, JUnit 5.

## Global Constraints

- Loom/Yarn/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `gradle.properties`만 참조한다.
- Mixin을 쓰지 않는다 — 이번 계획의 3D 렌더링은 전부 공개 `WorldRenderEvents`/`VertexConsumerProvider`/`BeaconBlockEntityRenderer` API로 가능함을 아래 "검증된 API 시그니처"에서 확인했다.
- 토글/설정 변경은 즉시(다음 틱부터) 반영.
- 알 수 없는 설정 id는 무시.
- 매 프레임/매 틱 무거운 작업 금지 — 미니맵 점 계산은 B4와 동일하게 틱 단위(20Hz)로.
- 모드 프로젝트 빌드는 `JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10"`로 `./gradlew.bat`를 실행한다.

## 검증된 API 시그니처 (추측 아님 — 실제 jar를 `javap`로 직접 뜯어 확인함, 2026-08-03)

```java
// net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents (fabric-rendering-v1)
public static final Event<WorldRenderEvents.AfterTranslucent> AFTER_TRANSLUCENT;
public interface WorldRenderEvents.AfterTranslucent {
    void afterTranslucent(WorldRenderContext context);
}

// net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
Camera camera();
net.minecraft.client.util.math.MatrixStack matrixStack();
net.minecraft.client.render.VertexConsumerProvider consumers();
net.minecraft.client.world.ClientWorld world();
net.minecraft.client.render.RenderTickCounter tickCounter();

// net.minecraft.client.render.RenderTickCounter
float getTickDelta(boolean);

// net.minecraft.client.render.Camera
net.minecraft.util.math.Vec3d getPos();

// net.minecraft.client.util.math.MatrixStack — B1에서 이미 검증된 것 재사용
void push();
void pop();
void translate(double, double, double);

// net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer
public static final net.minecraft.util.Identifier BEAM_TEXTURE;
public static void renderBeam(
    net.minecraft.client.util.math.MatrixStack matrices,
    net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
    net.minecraft.util.Identifier texture,
    float tickDelta, float heightScale, long worldTime,
    int yOffset, int maxY, int color, float widthScale, float glowScale);
// 바닐라 비콘 블록엔티티가 자기 빔을 그릴 때 쓰는 바로 그 메서드 — public static이라 그대로 재사용 가능.
// yOffset/maxY/color의 정확한 단위·포맷(ARGB인지 RGB인지 등)은 시그니처만으론 100% 확정 못 함 —
// Task 4에서 합리적인 기본값으로 시작하고 Task 7 실기기 검증에서 눈으로 보며 조정한다.

// net.minecraft.util.math.Vec3d
public final double x, y, z;

// net.minecraft.entity.Entity
Vec3d getPos();

// net.minecraft.entity.LivingEntity
float getHealth();

// net.minecraft.client.MinecraftClient
boolean isInSingleplayer();
net.minecraft.server.integrated.IntegratedServer getServer();
net.minecraft.client.network.ServerInfo getCurrentServerEntry();

// net.minecraft.server.MinecraftServer
net.minecraft.world.SaveProperties getSaveProperties();
// net.minecraft.world.SaveProperties
String getLevelName();
// net.minecraft.client.network.ServerInfo
public String address; // public 필드

// net.minecraft.registry.RegistryKey<T> — B4에서 이미 검증된 것 재사용
net.minecraft.util.Identifier getValue();

// net.minecraft.world.World — B4에서 이미 검증됨
RegistryKey<World> getRegistryKey();
```

**확인 안 된 채 남겨두는 것(런타임 시각 확인 필요, Task 7에서 검증)**:
- `BeaconBlockEntityRenderer.renderBeam(...)`의 `color`/`widthScale`/`glowScale`/`yOffset`/`maxY` 정확한 의미와 단위.
- `client.player.getHealth()`가 0 이하로 떨어지는 시점에 `getPos()`가 정말 리스폰 텔레포트 전 좌표(진짜 죽은 자리)를 가리키는지.
- 죽음 감지가 멀티플레이(서버가 권위 있는 죽음 판정을 하는 환경)에서도 클라이언트 쪽 health 필드만으로 정확히 잡히는지.

---

### Task 1: `DeathLocation` + `DeathLocationFilter` + `DeathDetector` — 순수 로직

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/death/DeathLocation.java`
- Create: `mod/src/main/java/com/cubeclient/mod/death/DeathLocationFilter.java`
- Create: `mod/src/main/java/com/cubeclient/mod/death/DeathDetector.java`
- Create: `mod/src/test/java/com/cubeclient/mod/death/DeathLocationFilterTest.java`
- Create: `mod/src/test/java/com/cubeclient/mod/death/DeathDetectorTest.java`

**Interfaces:**
- Consumes: 없음(순수, Minecraft 클래스 의존 전혀 없음 — 차원은 `RegistryKey<World>`가 아니라 문자열로 저장한다. B4의 `ChunkPos`가 static 초기화 때문에 순수 테스트에서 못 쓴 것과 같은 문제를 피하려는 목적도 있지만, 더 근본적으로 Gson으로 JSON에 직접 저장하기에도 문자열이 단순하다).
- Produces: `DeathLocation(String worldId, String dimensionId, double x, double y, double z)`(record), `DeathLocationFilter.forCurrentWorld(List<DeathLocation> all, String currentWorldId, String currentDimensionId) -> List<DeathLocation>`, `DeathDetector.isDeathEdge(float previousHealth, float currentHealth) -> boolean`. Task 2(저장소)가 `DeathLocation`을, Task 4(`DeathLocationDisplay`)와 Task 5(`TerrainMinimap` 확장)가 세 개 다 가져다 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// mod/src/test/java/com/cubeclient/mod/death/DeathDetectorTest.java
package com.cubeclient.mod.death;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathDetectorTest {

    @Test
    void fallingBelowZeroIsADeathEdge() {
        assertTrue(DeathDetector.isDeathEdge(2.0f, 0.0f));
    }

    @Test
    void alreadyDeadLastTickIsNotANewEdge() {
        assertFalse(DeathDetector.isDeathEdge(0.0f, 0.0f));
    }

    @Test
    void stayingAliveIsNotADeathEdge() {
        assertFalse(DeathDetector.isDeathEdge(5.0f, 3.0f));
    }

    @Test
    void negativeHealthCountsAsDead() {
        assertTrue(DeathDetector.isDeathEdge(1.0f, -1.0f));
    }
}
```

```java
// mod/src/test/java/com/cubeclient/mod/death/DeathLocationFilterTest.java
package com.cubeclient.mod.death;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLocationFilterTest {

    @Test
    void keepsOnlyMatchingWorldAndDimension() {
        DeathLocation match = new DeathLocation("singleplayer:World1", "minecraft:overworld", 1, 2, 3);
        DeathLocation wrongWorld = new DeathLocation("singleplayer:World2", "minecraft:overworld", 1, 2, 3);
        DeathLocation wrongDimension = new DeathLocation("singleplayer:World1", "minecraft:the_nether", 1, 2, 3);
        List<DeathLocation> all = List.of(match, wrongWorld, wrongDimension);

        List<DeathLocation> result = DeathLocationFilter.forCurrentWorld(all, "singleplayer:World1", "minecraft:overworld");

        assertEquals(1, result.size());
        assertTrue(result.contains(match));
    }

    @Test
    void emptyListStaysEmpty() {
        assertEquals(0, DeathLocationFilter.forCurrentWorld(List.of(), "any", "any").size());
    }

    @Test
    void multipleMatchesAllKept() {
        DeathLocation a = new DeathLocation("w", "d", 1, 2, 3);
        DeathLocation b = new DeathLocation("w", "d", 4, 5, 6);

        List<DeathLocation> result = DeathLocationFilter.forCurrentWorld(List.of(a, b), "w", "d");

        assertEquals(2, result.size());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.death.DeathDetectorTest" --tests "com.cubeclient.mod.death.DeathLocationFilterTest"
```
Expected: FAIL — `DeathLocation`/`DeathLocationFilter`/`DeathDetector` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
// mod/src/main/java/com/cubeclient/mod/death/DeathLocation.java
package com.cubeclient.mod.death;

/** 죽은 위치 기록 하나. dimensionId는 RegistryKey<World>가 아니라 문자열(예:
 * "minecraft:overworld")로 저장한다 — JSON 저장이 단순해지고, B4의 ChunkPos가 겪은 것과 같은
 * "static 초기화가 게임 부팅을 요구하는 클래스를 순수 계층에 끌어들이는" 위험도 원천적으로 없다. */
public record DeathLocation(String worldId, String dimensionId, double x, double y, double z) {}
```

```java
// mod/src/main/java/com/cubeclient/mod/death/DeathLocationFilter.java
package com.cubeclient.mod.death;

import java.util.List;
import java.util.stream.Collectors;

/** 저장된 죽은 위치 중 "지금 있는 월드/차원"과 일치하는 것만 걸러낸다. */
public final class DeathLocationFilter {
    private DeathLocationFilter() {}

    public static List<DeathLocation> forCurrentWorld(
            List<DeathLocation> all, String currentWorldId, String currentDimensionId) {
        return all.stream()
            .filter(loc -> loc.worldId().equals(currentWorldId) && loc.dimensionId().equals(currentDimensionId))
            .collect(Collectors.toList());
    }
}
```

```java
// mod/src/main/java/com/cubeclient/mod/death/DeathDetector.java
package com.cubeclient.mod.death;

/** 죽는 순간(하강 edge)만 잡는 순수 판정. 매 틱 반복 호출돼도 죽음 하나당 한 번만 true를 낸다 —
 * ToggleSprint의 눌림 edge 검출과 같은 패턴. */
public final class DeathDetector {
    private DeathDetector() {}

    public static boolean isDeathEdge(float previousHealth, float currentHealth) {
        return previousHealth > 0f && currentHealth <= 0f;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.death.DeathDetectorTest" --tests "com.cubeclient.mod.death.DeathLocationFilterTest"
```
Expected: PASS, 7개 테스트 전부(DeathDetector 4개 + DeathLocationFilter 3개).

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/death/DeathLocation.java mod/src/main/java/com/cubeclient/mod/death/DeathLocationFilter.java mod/src/main/java/com/cubeclient/mod/death/DeathDetector.java mod/src/test/java/com/cubeclient/mod/death/DeathDetectorTest.java mod/src/test/java/com/cubeclient/mod/death/DeathLocationFilterTest.java
git commit -m "Add DeathLocation, DeathLocationFilter, DeathDetector: pure death-location data and logic"
```

---

### Task 2: `DeathLocationStore` — JSON 저장소

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/death/DeathLocationStore.java`
- Create: `mod/src/test/java/com/cubeclient/mod/death/DeathLocationStoreTest.java`

**Interfaces:**
- Consumes: `DeathLocation`(Task 1).
- Produces: `DeathLocationStore(Path storeFile)`, `getAll() -> List<DeathLocation>`(읽기 전용 뷰), `add(DeathLocation) throws IOException`, `clearAll() throws IOException`. Task 4(`DeathLocationDisplay`)와 Task 5(`TerrainMinimap` 확장)가 인스턴스 하나를 공유해서 쓴다. Task 6이 UI에서 `clearAll()`을 호출한다.

이 클래스는 `Path`/`Files`/Gson만 다루는 순수 Java IO라 Minecraft 객체 의존이 없다 — `ConfigStore`와 같은 패턴(실패 시 빈 목록, JSON 손상 시 `.bak`으로 보존)이지만 완전히 별도 파일(`death-locations.json`)에 저장한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.death;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLocationStoreTest {

    @Test
    void emptyStoreWithNoFileReturnsEmptyList(@TempDir Path tempDir) {
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));

        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void addPersistsAndGetAllReflectsIt(@TempDir Path tempDir) throws IOException {
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));

        store.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));

        assertEquals(1, store.getAll().size());
        assertEquals("w", store.getAll().get(0).worldId());
    }

    @Test
    void newStoreInstanceReadsWhatAPreviousInstanceWrote(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("death-locations.json");
        DeathLocationStore first = new DeathLocationStore(file);
        first.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));

        DeathLocationStore second = new DeathLocationStore(file);

        assertEquals(1, second.getAll().size());
    }

    @Test
    void clearAllEmptiesTheStore(@TempDir Path tempDir) throws IOException {
        DeathLocationStore store = new DeathLocationStore(tempDir.resolve("death-locations.json"));
        store.add(new DeathLocation("w", "minecraft:overworld", 1.0, 64.0, 2.0));

        store.clearAll();

        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void corruptFileIsTreatedAsEmptyAndBackedUp(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("death-locations.json");
        Files.writeString(file, "{ not valid json [");

        DeathLocationStore store = new DeathLocationStore(file);

        assertTrue(store.getAll().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("death-locations.json.bak")));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.death.DeathLocationStoreTest"
```
Expected: FAIL — `DeathLocationStore` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.death;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 죽은 위치 목록을 mod-config.json과 별도 파일에 저장한다 — 기능 켜짐 설정과 성격이 다른
 * 데이터라서 ModConfig에 얹지 않는다. ConfigStore와 같은 실패 처리 패턴(빈 목록으로 취급,
 * JSON 손상 시 .bak으로 보존). */
public class DeathLocationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<DeathLocation>>() {}.getType();

    private final Path storeFile;
    private List<DeathLocation> cached;

    public DeathLocationStore(Path storeFile) {
        this.storeFile = storeFile;
    }

    public List<DeathLocation> getAll() {
        if (cached == null) {
            cached = loadOrEmpty();
        }
        return Collections.unmodifiableList(cached);
    }

    public void add(DeathLocation location) throws IOException {
        List<DeathLocation> updated = new ArrayList<>(getAll());
        updated.add(location);
        save(updated);
    }

    public void clearAll() throws IOException {
        save(new ArrayList<>());
    }

    private List<DeathLocation> loadOrEmpty() {
        if (!Files.exists(storeFile)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(storeFile);
            List<DeathLocation> loaded = GSON.fromJson(json, LIST_TYPE);
            return loaded == null ? new ArrayList<>() : loaded;
        } catch (IOException e) {
            return new ArrayList<>();
        } catch (JsonSyntaxException e) {
            backupCorruptFile();
            return new ArrayList<>();
        }
    }

    private void backupCorruptFile() {
        try {
            Path backup = storeFile.resolveSibling(storeFile.getFileName() + ".bak");
            Files.move(storeFile, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 백업조차 실패해도 빈 목록으로 계속 진행 — 크래시보다 낫다.
        }
    }

    private void save(List<DeathLocation> locations) throws IOException {
        if (storeFile.getParent() != null) {
            Files.createDirectories(storeFile.getParent());
        }
        Files.writeString(storeFile, GSON.toJson(locations, LIST_TYPE));
        cached = locations;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.death.DeathLocationStoreTest"
```
Expected: PASS, 5개 테스트 전부.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/death/DeathLocationStore.java mod/src/test/java/com/cubeclient/mod/death/DeathLocationStoreTest.java
git commit -m "Add DeathLocationStore: JSON-backed death location persistence"
```

---

### Task 3: `WorldIdentity` — 월드/서버 식별자 (Minecraft 객체 의존)

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/death/WorldIdentity.java`

**Interfaces:**
- Consumes: 없음(기존 코드 재사용 없음).
- Produces: `WorldIdentity.currentWorldId(MinecraftClient) -> String`, `WorldIdentity.currentDimensionId(World) -> String`. Task 4와 Task 5가 둘 다 가져다 쓴다.

이 클래스는 `MinecraftClient`/`World`를 직접 다뤄서 유닛 테스트 불가 — Task 4/9의 `ChunkColorSampler`와 같은 이유. 테스트 없이 구현만 하고 컴파일 확인, 실제 정확성은 Task 7 실기기 검증에서 확인한다.

- [ ] **Step 1: 구현 작성**

```java
package com.cubeclient.mod.death;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.world.World;

/** 죽은 위치를 어느 월드/서버에서 기록했는지 구분하는 문자열 키. 싱글플레이는 세이브 이름,
 * 멀티플레이는 서버 주소를 쓴다 — 같은 차원 종류(예: 오버월드)라도 실제로는 완전히 다른 물리적
 * 장소인 다른 월드/서버의 좌표와 섞이지 않게 하기 위함(B4의 MinimapChunkCache가 겪은
 * "같은 차원 키, 다른 World 인스턴스" 문제와 같은 종류의 함정). */
public final class WorldIdentity {
    private WorldIdentity() {}

    public static String currentWorldId(MinecraftClient client) {
        if (client.isInSingleplayer()) {
            return "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
        }
        ServerInfo serverEntry = client.getCurrentServerEntry();
        return serverEntry != null ? "server:" + serverEntry.address : "unknown";
    }

    public static String currentDimensionId(World world) {
        return world.getRegistryKey().getValue().toString();
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/death/WorldIdentity.java
git commit -m "Add WorldIdentity: singleplayer save name / multiplayer server address as a world-scoping key"
```

---

### Task 4: `DeathLocationDisplay` — 죽음 감지 + 3D 빔 렌더링

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/DeathLocationDisplay.java`

**Interfaces:**
- Consumes: `DeathLocation`/`DeathLocationFilter`/`DeathDetector`(Task 1), `DeathLocationStore`(Task 2), `WorldIdentity`(Task 3), `Feature`/`Category.WORLD`(B0).
- Produces: `DeathLocationDisplay(CachedConfig, DeathLocationStore)`. Task 6의 `CubeClientModClient` 등록이 이 시그니처를 그대로 쓴다.

- [ ] **Step 1: 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.death.DeathDetector;
import com.cubeclient.mod.death.DeathLocation;
import com.cubeclient.mod.death.DeathLocationFilter;
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.death.WorldIdentity;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.util.List;

public class DeathLocationDisplay implements Feature {
    // 실기기에서 눈으로 보고 조정할 값들 — BeaconBlockEntityRenderer.renderBeam의 정확한 단위가
    // 시그니처만으론 확정 안 됨(위 "확인 안 된 채 남겨두는 것" 참고).
    private static final int BEAM_COLOR = 0xFF0000;
    private static final float BEAM_WIDTH_SCALE = 0.4f;
    private static final float BEAM_GLOW_SCALE = 0.25f;
    private static final int BEAM_MAX_HEIGHT = 320;

    private final CachedConfig cachedConfig;
    private final DeathLocationStore store;
    // 로그인 직후 첫 틱에 거짓 죽음 판정이 안 나도록 양수로 시작.
    private float lastHealth = 1f;

    public DeathLocationDisplay(CachedConfig cachedConfig, DeathLocationStore store) {
        this.cachedConfig = cachedConfig;
        this.store = store;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onRenderBeams);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        float currentHealth = client.player.getHealth();
        if (cachedConfig.current().isEnabled(id()) && DeathDetector.isDeathEdge(lastHealth, currentHealth)) {
            recordDeath(client);
        }
        lastHealth = currentHealth;
    }

    private void recordDeath(MinecraftClient client) {
        String worldId = WorldIdentity.currentWorldId(client);
        String dimensionId = WorldIdentity.currentDimensionId(client.world);
        Vec3d pos = client.player.getPos();
        try {
            store.add(new DeathLocation(worldId, dimensionId, pos.x, pos.y, pos.z));
        } catch (IOException e) {
            client.player.sendMessage(Text.literal("죽은 위치를 저장하지 못했습니다: " + e.getMessage()), false);
        }
    }

    private void onRenderBeams(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !cachedConfig.current().isEnabled(id())) {
            return;
        }

        String worldId = WorldIdentity.currentWorldId(client);
        String dimensionId = WorldIdentity.currentDimensionId(client.world);
        List<DeathLocation> visible = DeathLocationFilter.forCurrentWorld(store.getAll(), worldId, dimensionId);
        if (visible.isEmpty()) {
            return;
        }

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        float tickDelta = context.tickCounter().getTickDelta(true);
        long worldTime = client.world.getTime();

        for (DeathLocation location : visible) {
            matrices.push();
            matrices.translate(
                location.x() - cameraPos.x, location.y() - cameraPos.y, location.z() - cameraPos.z);
            BeaconBlockEntityRenderer.renderBeam(
                matrices, consumers, BeaconBlockEntityRenderer.BEAM_TEXTURE,
                tickDelta, 1.0f, worldTime, 0, BEAM_MAX_HEIGHT, BEAM_COLOR,
                BEAM_WIDTH_SCALE, BEAM_GLOW_SCALE);
            matrices.pop();
        }
    }

    @Override
    public String id() {
        return "death_location";
    }

    @Override
    public String displayName() {
        return "죽은 위치 표시";
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/DeathLocationDisplay.java
git commit -m "Add DeathLocationDisplay: death detection + 3D beacon-beam rendering at death locations"
```

---

### Task 5: `TerrainMinimap`에 죽은 위치 점 추가

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/features/TerrainMinimap.java`

**Interfaces:**
- Consumes: `DeathLocation`/`DeathLocationFilter`(Task 1), `DeathLocationStore`(Task 2), `WorldIdentity`(Task 3), 기존 `TerrainMinimap`의 `Dot(int px, int py, int argb)` record와 `computeDots(...)`(B4 Task 6/11).
- Produces: `TerrainMinimap(CachedConfig, DeathLocationStore)`(생성자 시그니처 변경 — 기존 `TerrainMinimap(CachedConfig)`에서 인자 추가). Task 6의 `CubeClientModClient` 등록이 이 새 시그니처를 쓴다.

- [ ] **Step 1: 생성자와 필드 수정**

`mod/src/main/java/com/cubeclient/mod/features/TerrainMinimap.java`에서 필드 선언부를 찾아:

```java
    private final CachedConfig cachedConfig;
    private final MinimapChunkCache chunkCache = new MinimapChunkCache();
    private final KeyBinding minimapKey;
```

아래로 교체:

```java
    private final CachedConfig cachedConfig;
    private final MinimapChunkCache chunkCache = new MinimapChunkCache();
    private final DeathLocationStore deathLocationStore;
    private final KeyBinding minimapKey;
```

생성자를 찾아:

```java
    public TerrainMinimap(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
```

아래로 교체:

```java
    public TerrainMinimap(CachedConfig cachedConfig, DeathLocationStore deathLocationStore) {
        this.cachedConfig = cachedConfig;
        this.deathLocationStore = deathLocationStore;
```

파일 상단 import 목록에 추가:

```java
import com.cubeclient.mod.death.DeathLocation;
import com.cubeclient.mod.death.DeathLocationFilter;
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.death.WorldIdentity;
```

- [ ] **Step 2: `computeDots`가 죽은 위치도 같이 담도록 수정**

`computeDots` 메서드를 찾아(끝부분 `return dots;` 직전):

```java
            dots.add(new Dot(px, py, blipArgb(blip)));
        }
        return dots;
    }
```

아래로 교체:

```java
            dots.add(new Dot(px, py, blipArgb(blip)));
        }
        addDeathDots(dots, client, snappedPlayerX, snappedPlayerZ);
        return dots;
    }

    private static final int DEATH_MARKER_ARGB = 0xFF9B59B6;

    private void addDeathDots(List<Dot> dots, MinecraftClient client, double snappedPlayerX, double snappedPlayerZ) {
        String worldId = WorldIdentity.currentWorldId(client);
        String dimensionId = WorldIdentity.currentDimensionId(client.world);
        List<DeathLocation> visible =
            DeathLocationFilter.forCurrentWorld(deathLocationStore.getAll(), worldId, dimensionId);

        double half = TEXTURE_SIZE / 2.0;
        double blocksPerPixel = RADIUS_BLOCKS / half;
        for (DeathLocation location : visible) {
            double dx = location.x() - snappedPlayerX;
            double dz = location.z() - snappedPlayerZ;
            if (!MinimapMath.isColumnWithinRadius(dx, dz, RADIUS_BLOCKS)) {
                continue;
            }
            int px = (int) (dx / blocksPerPixel + half);
            int py = (int) (dz / blocksPerPixel + half);
            dots.add(new Dot(px, py, DEATH_MARKER_ARGB));
        }
    }
```

죽은 위치 점 색(`0xFF9B59B6`, 보라)은 기존 엔티티 점 3색(빨강/초록/흰색)과 겹치지 않는 색이다.

- [ ] **Step 3: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL — `TerrainMinimap`은 원래도 유닛 테스트가 없는 파일이라(Minecraft 렌더링 파이프라인에 직접 묶임) 이 변경이 새 테스트를 깨지 않는지만 전체 스위트로 확인한다.

- [ ] **Step 4: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/TerrainMinimap.java
git commit -m "Add death-location markers to TerrainMinimap, sharing DeathLocationStore with DeathLocationDisplay"
```

---

### Task 6: 모드 목록 화면에 "전체 삭제" 버튼 + 배선

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `DeathLocationStore`(Task 2), `DeathLocationDisplay`(Task 4), `TerrainMinimap`의 새 생성자(Task 5).
- Produces: 없음(이 계획의 마지막 통합 배선).

**왜 탭 줄이 아니라 화면 하단에 버튼을 두는지**: `ModListScreen`의 탭 줄은 이미 "전부" + 카테고리 4개 + "HUD 조절" = 6개 버튼이 320 단위 최소 너비 안에 꽉 차 있다(B0에서 "탭 5개+검색창이 폭 320에 안 들어간다"를 실제로 겪은 자리). 여기에 버튼을 더 추가하면 같은 문제가 재발할 수 있어, `HudEditorScreen`의 "나가기"/"위치 초기화"처럼 화면 하단에 별도로 둔다.

- [ ] **Step 1: `ModListScreen`에 필드·버튼·메서드 추가**

`mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`에서 생성자를 찾아:

```java
    private final Screen parent;
    private final FeatureRegistry registry;
    private final CachedConfig cachedConfig;
```

아래로 교체:

```java
    private final Screen parent;
    private final FeatureRegistry registry;
    private final CachedConfig cachedConfig;
    private final com.cubeclient.mod.death.DeathLocationStore deathLocationStore;
```

```java
    public ModListScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;
        this.registry = registry;
        this.cachedConfig = cachedConfig;
    }
```

아래로 교체:

```java
    public ModListScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig,
                          com.cubeclient.mod.death.DeathLocationStore deathLocationStore) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;
        this.registry = registry;
        this.cachedConfig = cachedConfig;
        this.deathLocationStore = deathLocationStore;
    }
```

`init()` 메서드 맨 끝(`rebuildCards();` 바로 앞)에 추가:

```java
        addDrawableChild(ButtonWidget.builder(Text.literal("죽은 위치 전체 삭제"), b -> clearDeathLocations())
            .dimensions(width - 160 - MARGIN, height - 20 - 8, 160, 20)
            .build());

        rebuildCards();
```

`persist()` 메서드 바로 다음에 새 메서드 추가:

```java
    private void clearDeathLocations() {
        try {
            deathLocationStore.clearAll();
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("죽은 위치를 삭제하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }
```

- [ ] **Step 2: `ClientSettingsButton`이 `DeathLocationStore`를 받아 넘기도록 수정**

`mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java`를 아래로 교체:

```java
package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class ClientSettingsButton {
    private ClientSettingsButton() {}

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int BOTTOM_MARGIN = 8;

    public static void register(FeatureRegistry registry, CachedConfig cachedConfig,
                                 DeathLocationStore deathLocationStore) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                addButton(
                    screen,
                    scaledWidth / 2 - BUTTON_WIDTH / 2,
                    rowBelowExistingButtons(screen, scaledHeight),
                    registry, cachedConfig, deathLocationStore);
            }
        });
    }

    private static int rowBelowExistingButtons(Screen screen, int scaledHeight) {
        int lowestBottom = 0;
        for (ClickableWidget widget : Screens.getButtons(screen)) {
            lowestBottom = Math.max(lowestBottom, widget.getY() + widget.getHeight());
        }

        int proposed = lowestBottom + ROW_GAP;
        int highestAllowed = scaledHeight - BUTTON_HEIGHT - BOTTOM_MARGIN;
        return Math.min(proposed, highestAllowed);
    }

    private static void addButton(Screen screen, int x, int y,
                                   FeatureRegistry registry, CachedConfig cachedConfig,
                                   DeathLocationStore deathLocationStore) {
        ButtonWidget button = ButtonWidget.builder(Text.literal("클라이언트 설정"), b -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(new ModListScreen(screen, registry, cachedConfig, deathLocationStore));
        }).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        Screens.getButtons(screen).add(button);
    }
}
```

(클래스·메서드 주석은 원본 파일 그대로 유지 — 이 스텝은 시그니처에 `DeathLocationStore` 매개변수를 추가하고 그걸 관통시키는 것 외엔 로직을 안 바꾼다.)

- [ ] **Step 3: `CubeClientModClient` 배선**

`mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`의 `onInitializeClient()`에서, `CachedConfig cachedConfig = new CachedConfig(new ConfigStore(configFile));` 다음 줄에 추가:

```java
        Path deathLocationsFile = ConfigStore.resolveConfigDir(fallback).resolve("death-locations.json");
        DeathLocationStore deathLocationStore = new DeathLocationStore(deathLocationsFile);
```

`registry.register(new TerrainMinimap(cachedConfig));` 줄을 찾아:

```java
        registry.register(new TerrainMinimap(cachedConfig));
```

아래로 교체:

```java
        registry.register(new TerrainMinimap(cachedConfig, deathLocationStore));
        registry.register(new DeathLocationDisplay(cachedConfig, deathLocationStore));
```

`ClientSettingsButton.register(registry, cachedConfig);` 줄을 찾아:

```java
        ClientSettingsButton.register(registry, cachedConfig);
```

아래로 교체:

```java
        ClientSettingsButton.register(registry, cachedConfig, deathLocationStore);
```

import 목록에 추가:

```java
import com.cubeclient.mod.death.DeathLocationStore;
import com.cubeclient.mod.features.DeathLocationDisplay;
```

- [ ] **Step 4: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL, 지금까지 만든 유닛 테스트 전부(다른 서브프로젝트 포함) 통과.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Wire DeathLocationDisplay and the death-location clear-all button into the mod list screen"
```

---

### Task 7: 실기기 수동 검증

**Files:** 없음.

**Interfaces:**
- Consumes: Task 1~6 전체.
- Produces: 없음 — 발견된 문제는 이전 태스크로 돌아가 수정.

- [ ] **Step 1: 빌드 및 배포**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat build
```
빌드된 jar를 두 인스턴스(`%APPDATA%\CubeClient\instances\fabric-1.21.4\mods\`, `fabric-1.21\mods\`)에 복사.

- [ ] **Step 2: 실행 및 확인**

큐브클라이언트 런처로 게임 실행 → 모드 목록 화면에서 "죽은 위치 표시" 켜기 → 싱글플레이 월드에서 죽어보기.

확인 항목:
- 죽은 자리에 실제로 빛나는 빔이 생기는지, 색·굵기·밝기가 눈에 거슬리지 않는 수준인지(안 맞으면 `DeathLocationDisplay`의 `BEAM_COLOR`/`BEAM_WIDTH_SCALE`/`BEAM_GLOW_SCALE` 조정).
- 미니맵에도 같은 위치에 보라색 점이 뜨는지.
- 리스폰 후 실제로 죽은 자리로 돌아가보면 빔 위치가 정확한지(리스폰 스폰 위치가 아니라 진짜 죽은 자리인지).
- 여러 번 죽으면 전부 동시에 표시되는지.
- 다른 차원(네더 등)으로 가면 그 차원 것만 안 보이는지, 원래 차원으로 돌아오면 다시 보이는지.
- (가능하면) 다른 월드에 접속해서 이전 월드의 죽은 위치가 안 보이는지.
- 모드 목록 화면의 "죽은 위치 전체 삭제" 버튼을 누르면 실제로 다 사라지는지(미니맵 점·3D 빔 둘 다).
- 기능을 꺼두면 죽어도 기록이 안 되는지, 빔도 안 보이는지.
- FPS 저하가 눈에 띄지 않는지(죽은 위치가 여러 개 쌓였을 때도).

- [ ] **Step 3: 발견된 문제 수정 및 재검증**

문제가 있으면 해당 태스크 파일을 직접 고치고, 관련 유닛 테스트가 있으면 다시 돌리고, 없으면 다시 빌드해서 재배포·재확인한다.

- [ ] **Step 4: 최종 커밋**

```bash
git add -A
git commit -m "Fix issues found during B5 death-location real-device verification"
```
(문제가 없었다면 이 스텝은 생략.)
