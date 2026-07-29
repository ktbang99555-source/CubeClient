# Sub-project B1: HUD 프레임워크 + 위치/크기 편집기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HUD 요소가 화면 위 위치·크기를 갖고 마우스로 조절 가능하게 만드는 프레임워크를 구축하고, 그 프레임워크로 속도·CPS·성능(CPU+RAM) 세 기능을 추가한다. 동시에 B0에서 미뤄둔 두 가지(deprecated `HudRenderCallback`, 모드 목록 화면 스크롤 없음)를 해소한다.

**Architecture:** `Feature`를 그대로 두고 `PositionedHudFeature extends Feature`라는 얇은 서브인터페이스를 새로 만들어 화면에 좌표를 가진 기능만 구현하게 한다. 위치·크기는 `HudPosition`(화면 비율 + 배율) record로 표현하고 `ModConfig`에 `Map<String, HudPosition>`으로 저장한다. 렌더링은 deprecated `HudRenderCallback`을 버리고 `HudLayerRegistrationCallback` + `IdentifiedLayer`로 옮긴다. 위치·크기 편집은 새 `HudEditorScreen`(드래그 이동 + 모서리 핸들 리사이즈)에서 하며, `ModListScreen`에 탭 하나("HUD 조절")를 추가해 진입점을 만든다.

**Tech Stack:** Fabric Loom 1.10.2, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader `0.19.3`, Fabric API `0.119.4+1.21.4`, JDK 21, JUnit 5, Gson 2.11.0.

## Global Constraints

- Loom/Yarn/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `gradle.properties`만 참조한다.
- Mixin을 쓰지 않는다 — 모든 후킹은 public Fabric API 이벤트로 한다.
- 색상은 `com.cubeclient.mod.gui.Theme` 상수만 쓴다 (`GROUND`, `PANEL`, `BORDER`, `TEXT`, `MUTED`, `ACCENT`, `WARNING`).
- 토글/설정 변경은 즉시(다음 프레임부터) 반영되어야 한다 — 게임 재시작 필요 없이.
- 알 수 없는 설정 id는 무시한다(에러 아님).
- GPU 사용률은 이번 서브프로젝트 범위 밖 — CPU+RAM만.
- `HudEditorScreen`이 열려 있는 동안 게임을 계속 진행시키려는 로직을 만들지 않는다 — `Screen` 상속의 기본 동작(싱글플레이 자연 일시정지, 멀티는 계속 진행)을 그대로 둔다.
- 모드 프로젝트 빌드는 `JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10"`로 `./gradlew.bat`를 실행한다(중첩 디렉터리 정확히 지정 — 부모 `jdk21`만 주면 실패).

## 검증된 API 시그니처 (추측 아님 — 실제 jar 리플렉션 + 실제 컴파일로 확인됨)

이 계획 작성 중 아래 시그니처들을 `javap`로 실제 jar를 뜯어 확인하고, 마지막엔 `mod/`에 스크래치 클래스를 만들어 `./gradlew.bat compileJava`로 실제 컴파일까지 통과시켜 재검증했다. 아래 그대로 쓰면 된다.

```java
// net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback
Event<HudLayerRegistrationCallback> EVENT;
void register(LayeredDrawerWrapper drawer); // 함수형 인터페이스 메서드

// net.fabricmc.fabric.api.client.rendering.v1.LayeredDrawerWrapper
LayeredDrawerWrapper attachLayerAfter(Identifier anchor, IdentifiedLayer layer);

// net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer
Identifier MISC_OVERLAYS; // 다른 상수도 있음 (CROSSHAIR, HOTBAR_AND_BARS 등), B1은 MISC_OVERLAYS만 씀
static IdentifiedLayer of(Identifier id, /* (DrawContext, RenderTickCounter) -> void */ layerRenderer);

// net.minecraft.util.Identifier
static Identifier of(String namespace, String path);

// net.minecraft.client.option.GameOptions
public final KeyBinding attackKey; // MinecraftClient.getInstance().options.attackKey

// net.minecraft.client.option.KeyBinding
public boolean wasPressed(); // 호출할 때마다 큐를 1개씩 소모, 그 소모가 있었으면 true

// net.minecraft.entity.Entity (ClientPlayerEntity가 상속)
public final double getX();
public final double getY();
public final double getZ();

// net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
Event<ClientTickEvents.EndTick> END_CLIENT_TICK;
void onEndTick(MinecraftClient client); // EndTick 함수형 인터페이스 메서드

// net.minecraft.client.util.Window (MinecraftClient.getInstance().getWindow())
public int getScaledWidth();
public int getScaledHeight();

// net.minecraft.client.gui.DrawContext (B0에서 이미 확인된 것 + 스크롤용 추가분)
public void enableScissor(int x1, int y1, int x2, int y2);
public void disableScissor();
public MatrixStack getMatrices();

// net.minecraft.client.util.math.MatrixStack — 텍스트 스케일링용. push()/pop() (pushMatrix
// 아님), scale은 인자 2개가 아니라 3개(x, y, z).
public void push();
public void scale(float x, float y, float z);
public void pop();
```

---

### Task 1: `HudPosition` record

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/gui/HudPosition.java`
- Test: `mod/src/test/java/com/cubeclient/mod/gui/HudPositionTest.java`

**Interfaces:**
- Consumes: 없음 (순수 로직, 외부 의존 없음)
- Produces: `HudPosition(double xRatio, double yRatio, double scale)` record. 정적 팩토리 `HudPosition.of(double, double, double)`. 이후 태스크가 이 타입을 `ModConfig`, `PositionedHudFeature`, `HudEditorScreen`에서 그대로 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudPositionTest {

    @Test
    void valuesWithinRangePassThroughUnchanged() {
        HudPosition pos = HudPosition.of(0.25, 0.5, 1.5);

        assertEquals(0.25, pos.xRatio());
        assertEquals(0.5, pos.yRatio());
        assertEquals(1.5, pos.scale());
    }

    @Test
    void negativeRatiosClampToZero() {
        HudPosition pos = HudPosition.of(-0.3, -1.0, 1.0);

        assertEquals(0.0, pos.xRatio());
        assertEquals(0.0, pos.yRatio());
    }

    @Test
    void ratiosAboveOneClampToOne() {
        HudPosition pos = HudPosition.of(1.5, 2.0, 1.0);

        assertEquals(1.0, pos.xRatio());
        assertEquals(1.0, pos.yRatio());
    }

    @Test
    void scaleBelowHalfClampsToHalf() {
        HudPosition pos = HudPosition.of(0.0, 0.0, 0.1);

        assertEquals(0.5, pos.scale());
    }

    @Test
    void scaleAboveThreeClampsToThree() {
        HudPosition pos = HudPosition.of(0.0, 0.0, 10.0);

        assertEquals(3.0, pos.scale());
    }

    // Gson deserialises a record by calling its canonical constructor directly through
    // reflection, never the static of() factory — the compact constructor has to clamp on its
    // own or a hand-edited config file could smuggle an out-of-range value past of() entirely.
    // Proven by calling the canonical constructor directly, bypassing of() the way Gson does.
    @Test
    void theCanonicalConstructorClampsTooNotJustTheFactory() {
        HudPosition pos = new HudPosition(-5.0, 5.0, 100.0);

        assertEquals(0.0, pos.xRatio());
        assertEquals(1.0, pos.yRatio());
        assertEquals(3.0, pos.scale());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.gui.HudPositionTest"
```
Expected: FAIL — `HudPosition` 클래스가 없어서 컴파일 자체가 안 됨.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.gui;

/**
 * 화면 비율(0.0~1.0) + 배율로 저장하는 HUD 요소 위치. 절대 픽셀이 아니라 비율인 이유는 창
 * 크기·GUI 배율이 바뀌어도 상대 위치가 유지되게 하기 위함. 컴팩트 생성자에서 클램프하는
 * 이유는 HudPositionTest.theCanonicalConstructorClampsTooNotJustTheFactory 참고.
 */
public record HudPosition(double xRatio, double yRatio, double scale) {
    public HudPosition {
        xRatio = clamp(xRatio, 0.0, 1.0);
        yRatio = clamp(yRatio, 0.0, 1.0);
        scale = clamp(scale, 0.5, 3.0);
    }

    public static HudPosition of(double xRatio, double yRatio, double scale) {
        return new HudPosition(xRatio, yRatio, scale);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.gui.HudPositionTest"
```
Expected: PASS, 6개 테스트 전부.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/HudPosition.java mod/src/test/java/com/cubeclient/mod/gui/HudPositionTest.java
git commit -m "Add HudPosition: screen-ratio coordinates with clamping in the canonical constructor"
```

---

### Task 2: `ModConfig`에 `positions` 필드 추가

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/config/ModConfig.java`
- Modify: `mod/src/test/java/com/cubeclient/mod/config/ConfigStoreTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`

**Interfaces:**
- Consumes: `HudPosition` (Task 1).
- Produces: `ModConfig(Map<String, Boolean> enabled, Set<String> favorites, Map<String, HudPosition> positions)`. 새 메서드 `ModConfig.positionOr(String featureId, HudPosition fallback)`. 이후 태스크(3, 9)가 이 시그니처를 그대로 쓴다.

**주의:** `ModConfig`는 record라 필드 추가 시 생성자 인자 순서가 바뀐다. 기존 `new ModConfig(Map, Set)` 2-인자 호출부가 세 군데 있다 — 그대로 두면 이 태스크가 끝나자마자 컴파일이 깨진다:
- `ConfigStoreTest.java`의 `savedConfigRoundTripsThroughLoad`, `savingCreatesParentDirectories`.
- `ModListScreen.java`의 `onToggle()`/`onFavoriteToggle()` — 이 두 곳은 단순히 3번째 인자에 `Map.of()`를 채우면 **토글 한 번 누를 때마다 저장된 HUD 위치가 통째로 사라지는 실제 버그**가 된다(이 시점엔 아직 `positions`를 채울 방법이 없어 보이지만, 기존 `config.positions()`를 그대로 넘기면 된다 — 아래 Step 3에서 같이 고친다).

- [ ] **Step 1: 실패하는 테스트 작성**

`mod/src/test/java/com/cubeclient/mod/config/ConfigStoreTest.java`에 아래 테스트들을 추가한다 (기존 테스트는 그대로 두되, `new ModConfig(Map.of("fps", true), Set.of("fps"))`처럼 2개 인자로 생성자를 호출하는 기존 줄들은 `Map.of()`를 세 번째 인자로 추가해서 3개 인자로 고친다 — `savedConfigRoundTripsThroughLoad`의 `new ModConfig(Map.of("fps", true), Set.of("fps"))` → `new ModConfig(Map.of("fps", true), Set.of("fps"), Map.of())`, `savingCreatesParentDirectories`의 `new ModConfig(Map.of(), Set.of())` → `new ModConfig(Map.of(), Set.of(), Map.of())`):

```java
    // Gson이 없는 필드를 null로 채우는 문제(ModConfig의 기존 두 필드에서 이미 겪음)가
    // positions 필드에도 똑같이 적용되는지 확인한다.
    @Test
    void aConfigMissingThePositionsKeyLoadsWithAnEmptyPositionsMap() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": { "fps": true }, "favorites": [] }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertNotNull(loaded.positions(), "a missing 'positions' key must not become null");
        assertTrue(loaded.positions().isEmpty());
    }

    @Test
    void positionOrReturnsTheStoredPositionWhenPresent() {
        HudPosition stored = HudPosition.of(0.2, 0.3, 1.0);
        ModConfig config = new ModConfig(Map.of(), Set.of(), Map.of("speed", stored));

        HudPosition result = config.positionOr("speed", HudPosition.of(0.0, 0.0, 1.0));

        assertEquals(stored, result);
    }

    @Test
    void positionOrReturnsTheFallbackWhenNothingIsStoredForThatId() {
        ModConfig config = ModConfig.empty();
        HudPosition fallback = HudPosition.of(0.1, 0.1, 1.0);

        HudPosition result = config.positionOr("speed", fallback);

        assertEquals(fallback, result);
    }

    @Test
    void positionsMapIsImmutableJustLikeTheOtherTwoFields() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        Files.writeString(file, """
            { "enabled": {}, "favorites": [], "positions": { "speed": { "xRatio": 0.1, "yRatio": 0.1, "scale": 1.0 } } }
            """);

        ModConfig loaded = new ConfigStore(file).load();

        assertThrows(UnsupportedOperationException.class,
            () -> loaded.positions().put("cps", HudPosition.of(0.0, 0.0, 1.0)));
    }
```

파일 상단 import에 `com.cubeclient.mod.gui.HudPosition;`을 추가한다.

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.config.ConfigStoreTest"
```
Expected: FAIL — 컴파일 에러(`ModConfig`에 `positions()`/3-인자 생성자가 없음).

- [ ] **Step 3: 최소 구현 작성**

`mod/src/main/java/com/cubeclient/mod/config/ModConfig.java` 전체를 다음으로 교체:

```java
package com.cubeclient.mod.config;

import com.cubeclient.mod.gui.HudPosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 이 모드가 저장하는 전부: 어떤 기능 id가 켜졌는지, 어떤 게 즐겨찾기인지, 그리고 (B1부터)
 * 어떤 기능이 어디에 얼마나 크게 그려지는지. 여전히 평평하고 Gson 친화적 — 중첩 객체나
 * 커스텀 (역)직렬화기 불필요(HudPosition 자체도 세 개 double 필드뿐인 record).
 */
public record ModConfig(
    Map<String, Boolean> enabled,
    Set<String> favorites,
    Map<String, HudPosition> positions
) {

    /**
     * Gson이 캐노니컬 생성자를 리플렉션으로 직접 호출하며 JSON에 없는 필드는 null로 채운다
     * (ModConfig가 record라서 생기는 동작, B0에서 이미 확인됨). positions도 같은 문제를
     * 겪으므로 여기서 동일하게 null을 빈 컬렉션으로 정규화한다.
     */
    public ModConfig {
        enabled = enabled == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(enabled));
        favorites = favorites == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(favorites));
        positions = positions == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(positions));
    }

    public static ModConfig empty() {
        return new ModConfig(Map.of(), Set.of(), Map.of());
    }

    public boolean isEnabled(String featureId) {
        Boolean value = enabled.get(featureId);
        return value != null && value;
    }

    /** 옮겨진 적 없는 기능은 fallback(보통 feature.defaultPosition())을 쓴다. */
    public HudPosition positionOr(String featureId, HudPosition fallback) {
        return positions.getOrDefault(featureId, fallback);
    }
}
```

- [ ] **Step 4: `ModListScreen`의 두 호출부를 3-인자로 고치면서 `positions` 보존**

`mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`에서:

```java
    private void onToggle(Feature feature) {
        Map<String, Boolean> enabled = new HashMap<>(config.enabled());
        enabled.put(feature.id(), !config.isEnabled(feature.id()));
        config = new ModConfig(enabled, config.favorites(), config.positions());
        persist();
    }
```

```java
    private void onFavoriteToggle(Feature feature) {
        Set<String> favorites = new HashSet<>(config.favorites());
        if (!favorites.remove(feature.id())) {
            favorites.add(feature.id());
        }
        config = new ModConfig(config.enabled(), favorites, config.positions());
        persist();
        rebuildQueued = true;
    }
```

(각 메서드의 마지막 줄 `persist();`/`rebuildQueued = true;`는 기존 그대로 유지 — 바뀌는 건 `new ModConfig(...)` 호출의 인자 개수뿐이다.)

- [ ] **Step 5: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: PASS 전체 — `ConfigStoreTest`뿐 아니라 모드 프로젝트 전체(컴파일 에러 없이).

- [ ] **Step 6: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/config/ModConfig.java mod/src/test/java/com/cubeclient/mod/config/ConfigStoreTest.java mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java
git commit -m "Add per-feature HUD positions to ModConfig, preserving them across toggle/favorite edits"
```

---

### Task 3: `PositionedHudFeature` + `HudLayerRegistrationCallback` 전환 + `FpsDisplay` 마이그레이션 + 설정 캐시

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/registry/PositionedHudFeature.java`
- Create: `mod/src/main/java/com/cubeclient/mod/gui/HudRenderUtil.java`
- Create: `mod/src/main/java/com/cubeclient/mod/config/CachedConfig.java`
- Create: `mod/src/test/java/com/cubeclient/mod/config/CachedConfigTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/features/FpsDisplay.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `HudPosition`(Task 1), `ModConfig`/`ConfigStore`(Task 2, 기존).
- Produces: `PositionedHudFeature extends Feature` — `HudPosition defaultPosition()`, `void render(DrawContext context, HudPosition resolvedPosition)`. `HudRenderUtil.drawScaledText(DrawContext, HudPosition, HudRenderUtil.TextDrawer)` — Task 4/5/6이 각자의 `render()`에서 이 헬퍼를 호출해 push/scale/pop 보일러플레이트를 반복하지 않는다. `CachedConfig` — `ModConfig current()`, `void save(ModConfig)`(disk에 쓰고 캐시도 갱신). Task 4/5/6이 `PositionedHudFeature`와 `HudRenderUtil`을 쓰고, Task 8/9가 `CachedConfig`를 읽고 쓴다.

이 태스크가 이 계획에서 제일 크다 — 쪼개면 중간 상태가 컴파일이 안 되거나(레이어 전환 절반만 된 상태) 테스트가 애매해져서 하나로 묶는다.

- [ ] **Step 1: `CachedConfig`의 실패하는 테스트 작성**

```java
package com.cubeclient.mod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void currentReadsFromDiskOnFirstCall() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        store.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        CachedConfig cached = new CachedConfig(store);

        assertTrue(cached.current().isEnabled("fps"));
    }

    // 여러 HUD 기능이 프레임마다 각자 디스크를 읽는 걸 막는 게 이 클래스의 목적이므로, 디스크를
    // 우회해서 파일을 바꿔도 save()를 거치지 않으면 current()가 옛 값을 계속 돌려줘야 한다 —
    // 캐시가 실제로 캐시 역할을 하는지 증명한다.
    @Test
    void currentDoesNotReReadDiskAfterTheFirstCall() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        store.save(new ModConfig(Map.of("fps", false), Set.of(), Map.of()));
        CachedConfig cached = new CachedConfig(store);
        cached.current();

        store.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        assertEquals(false, cached.current().isEnabled("fps"));
    }

    @Test
    void saveWritesToDiskAndUpdatesTheCacheImmediately() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        ConfigStore store = new ConfigStore(file);
        CachedConfig cached = new CachedConfig(store);

        cached.save(new ModConfig(Map.of("fps", true), Set.of(), Map.of()));

        assertTrue(cached.current().isEnabled("fps"));
        assertTrue(new ConfigStore(file).load().isEnabled("fps"));
    }

    @Test
    void aBrokenDiskReadOnFirstAccessFallsBackToEmptyRatherThanThrowing() throws IOException {
        Path file = tempDir.resolve("mod-config.json");
        java.nio.file.Files.writeString(file, "{ not valid json");
        CachedConfig cached = new CachedConfig(new ConfigStore(file));

        assertTrue(cached.current().enabled().isEmpty());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.config.CachedConfigTest"
```
Expected: FAIL — `CachedConfig` 클래스 없음.

- [ ] **Step 3: `PositionedHudFeature` 작성**

```java
package com.cubeclient.mod.registry;

import com.cubeclient.mod.gui.HudPosition;
import net.minecraft.client.gui.DrawContext;

/**
 * 화면 위에 실제로 그려지고 위치·크기를 갖는 기능. Feature 자체에 이 메서드들을 넣지 않는
 * 이유: 조작·월드·서버 카테고리의 기능(예: Toggle Sneak/Sprint) 상당수는 화면에 좌표를 가진
 * 요소가 아니라서, 여기 넣으면 그런 기능들도 안 쓸 메서드를 강제로 구현하게 된다.
 */
public interface PositionedHudFeature extends Feature {
    HudPosition defaultPosition();
    void render(DrawContext context, HudPosition resolvedPosition);
}
```

- [ ] **Step 4: `HudRenderUtil` 작성 — push/scale/pop 보일러플레이트를 한 곳에 모음**

`FpsDisplay`(이 태스크)와 `SpeedDisplay`/`CpsDisplay`/`PerformanceDisplay`(Task 4~6)가 전부 "비율 좌표를 스케일된 픽셀로 바꾸고, 행렬을 push/scale/pop으로 감싸고, 그 안에서 텍스트 하나를 그린다"는 동일한 절차를 반복한다. 네 파일에 그대로 복제하면 리뷰에서 중복으로 지적받으므로 미리 뽑아둔다 — 각 기능의 `render()`에는 실제로 다른 부분(텍스트 내용)만 남는다.

```java
package com.cubeclient.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * PositionedHudFeature 구현체가 공유하는 렌더링 절차: 비율 좌표를 화면 배율 기준 픽셀로
 * 바꾸고, 행렬 스택을 push/scale/pop으로 감싸 배율을 적용한 뒤, 그 안에서 실제 텍스트를
 * 그린다. 이 세 단계가 FpsDisplay/SpeedDisplay/CpsDisplay/PerformanceDisplay에서 전부
 * 그대로 반복되므로, 각 기능은 텍스트 내용만 다른 이 헬퍼를 호출한다.
 */
public final class HudRenderUtil {
    private HudRenderUtil() {}

    public static void drawScaledText(DrawContext context, HudPosition pos, TextDrawer drawer) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = (int) (pos.xRatio() * client.getWindow().getScaledWidth());
        int y = (int) (pos.yRatio() * client.getWindow().getScaledHeight());
        float scale = (float) pos.scale();
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        drawer.draw(context, (int) (x / scale), (int) (y / scale));
        context.getMatrices().pop();
    }

    @FunctionalInterface
    public interface TextDrawer {
        void draw(DrawContext context, int x, int y);
    }
}
```

- [ ] **Step 5: `CachedConfig` 최소 구현**

```java
package com.cubeclient.mod.config;

import java.io.IOException;

/**
 * ConfigStore를 감싸서 매 프레임 디스크를 읽지 않게 하는 인메모리 캐시. 이 모드 프로세스
 * 안에서 설정을 쓰는 경로는 save()뿐이므로, 캐시와 디스크가 어긋날 일이 없다.
 */
public class CachedConfig {
    private final ConfigStore store;
    private ModConfig cached;

    public CachedConfig(ConfigStore store) {
        this.store = store;
    }

    public ModConfig current() {
        if (cached == null) {
            cached = loadOrEmpty();
        }
        return cached;
    }

    public void save(ModConfig config) throws IOException {
        store.save(config);
        cached = config;
    }

    private ModConfig loadOrEmpty() {
        try {
            return store.load();
        } catch (IOException e) {
            return ModConfig.empty();
        }
    }
}
```

- [ ] **Step 6: `CachedConfig` 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.config.CachedConfigTest"
```
Expected: PASS, 4개 테스트 전부.

- [ ] **Step 7: `FpsDisplay`를 `PositionedHudFeature`로 마이그레이션**

`mod/src/main/java/com/cubeclient/mod/features/FpsDisplay.java` 전체를 다음으로 교체:

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class FpsDisplay implements PositionedHudFeature {
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

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.01, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getCurrentFps() + " FPS";
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
```

**검증됨:** `DrawContext.getMatrices()` → `MatrixStack`, 메서드는 `push()`/`scale(float, float, float)`/`pop()`(2개 인자 아님, z축까지 3개) — 스크래치 클래스로 실제 컴파일까지 통과시켜 확인함(추측 아님). `HudRenderUtil.drawScaledText`는 Step 4에서 만든 헬퍼가 내부적으로 이 세 호출을 그대로 감싼다.

- [ ] **Step 8: 컴파일 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: `CubeClientModClient`를 새 레이어 시스템으로 전환**

`mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java` 전체를 다음으로 교체:

```java
package com.cubeclient.mod;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.features.FpsDisplay;
import com.cubeclient.mod.gui.ClientSettingsButton;
import com.cubeclient.mod.registry.FeatureRegistry;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

public class CubeClientModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path fallback = FabricLoader.getInstance().getConfigDir().resolve("cubeclient");
        Path configFile = ConfigStore.resolveConfigDir(fallback).resolve("mod-config.json");
        CachedConfig cachedConfig = new CachedConfig(new ConfigStore(configFile));

        FeatureRegistry registry = new FeatureRegistry();
        registry.register(new FpsDisplay());

        ClientSettingsButton.register(registry, cachedConfig);

        // HudRenderCallback은 사용 중인 Fabric API 버전에서 @Deprecated로 표시되어 있다
        // (jar를 풀어 javap -v로 RuntimeVisibleAnnotations에서 확인함). 대체 API인
        // HudLayerRegistrationCallback + IdentifiedLayer로 레이어 하나만 등록하고, 그 안에서
        // 켜진 PositionedHudFeature를 전부 순회해 그린다 — 기능 토글마다 레이어를
        // 등록/해제하지 않는다.
        HudLayerRegistrationCallback.EVENT.register(drawer ->
            drawer.attachLayerAfter(IdentifiedLayer.MISC_OVERLAYS, IdentifiedLayer.of(
                Identifier.of("cubeclient", "hud"),
                (context, tickCounter) -> {
                    var config = cachedConfig.current();
                    for (var feature : registry.all()) {
                        if (feature instanceof PositionedHudFeature hudFeature
                            && config.isEnabled(hudFeature.id())) {
                            var position = config.positionOr(hudFeature.id(), hudFeature.defaultPosition());
                            hudFeature.render(context, position);
                        }
                    }
                }
            ))
        );
    }
}
```

**참고:** `ClientSettingsButton.register`의 두 번째 인자 타입이 아직은 (B0 시절의) `ConfigStore`다. 바로 다음 Step에서 `ClientSettingsButton`과 `ModListScreen`도 `CachedConfig`를 받도록 같이 고친다 — 지금 이 Step만 반영한 상태로는 컴파일이 깨져도 된다.

- [ ] **Step 10: `ClientSettingsButton`이 `CachedConfig`를 받도록 최소 수정**

`mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java`에서 `ConfigStore configStore` 파라미터를 쓰는 두 곳(`register` 메서드 시그니처, `addButton` 메서드 시그니처와 그 안의 `new ModListScreen(screen, registry, configStore)` 호출)을 `CachedConfig cachedConfig`로 바꾸고, import를 `com.cubeclient.mod.config.ConfigStore` → `com.cubeclient.mod.config.CachedConfig`로 바꾼다. `ModListScreen`의 생성자 시그니처도 Task 8에서 `CachedConfig`를 받도록 바뀔 예정이므로, 지금은 `ModListScreen` 쪽에서 컴파일 에러가 나는 게 정상이다 — 다음 Step에서 고친다.

`mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`에서 `ConfigStore configStore` 필드와 생성자 파라미터, `loadConfigOrEmpty()`/`persist()` 메서드를 다음처럼 고친다:

```java
    // 필드 선언부에서
    private final CachedConfig cachedConfig;

    // 생성자에서
    public ModListScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;
        this.registry = registry;
        this.cachedConfig = cachedConfig;
    }
```

```java
    private ModConfig loadConfigOrEmpty() {
        return cachedConfig.current();
    }
```

```java
    private void persist() {
        try {
            cachedConfig.save(config);
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("설정을 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }
```

import에서 `com.cubeclient.mod.config.ConfigStore`를 지우고 `com.cubeclient.mod.config.CachedConfig`를 추가한다. `close()` 안의 `client.setScreen(parent)`는 그대로 둔다.

- [ ] **Step 11: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL, 이전까지의 모든 테스트(HudPositionTest, CachedConfigTest, ConfigStoreTest, FeatureRegistryTest) 통과.

- [ ] **Step 12: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/registry/PositionedHudFeature.java mod/src/main/java/com/cubeclient/mod/gui/HudRenderUtil.java mod/src/main/java/com/cubeclient/mod/config/CachedConfig.java mod/src/test/java/com/cubeclient/mod/config/CachedConfigTest.java mod/src/main/java/com/cubeclient/mod/features/FpsDisplay.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java mod/src/main/java/com/cubeclient/mod/gui/ClientSettingsButton.java mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java
git commit -m "Move HUD rendering off deprecated HudRenderCallback onto HudLayerRegistrationCallback"
```

---

### Task 4: `SpeedDisplay`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/SpeedDisplay.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/SpeedDisplayTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(Task 3), `HudPosition`(Task 1), `HudRenderUtil.drawScaledText`(Task 3).
- Produces: `SpeedDisplay` — 순수 계산 부분(`static double horizontalSpeed(double dx, double dz, double deltaSeconds)`)은 Minecraft 클래스 없이 유닛 테스트 가능하게 `public static`으로 노출한다. 이후 태스크가 참조하지 않음(B1의 마지막 HUD 기능이 아니라 그냥 독립적인 기능 중 하나).

**참고:** 실제 좌표 추적(`ClientTickEvents.END_CLIENT_TICK`, `MinecraftClient.player.getX()`)은 유닛 테스트가 닿지 않는 영역이라 이 태스크 내에서 순수 계산 함수와 그 함수를 호출하는 얇은 틱 리스너로 나눈다 — 계산 로직만 테스트한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedDisplayTest {

    @Test
    void movingThreeUnitsInOneSecondIsThreeMetersPerSecond() {
        double speed = SpeedDisplay.horizontalSpeed(3.0, 0.0, 1.0);

        assertEquals(3.0, speed, 0.0001);
    }

    // 수평만 잰다 — 낙하·비행 중 수직 성분이 값에 섞이면 안 된다.
    @Test
    void diagonalMovementUsesPythagoreanDistanceOnXZOnly() {
        double speed = SpeedDisplay.horizontalSpeed(3.0, 4.0, 1.0);

        assertEquals(5.0, speed, 0.0001);
    }

    @Test
    void halfSecondTickIntervalDoublesTheRate() {
        double speed = SpeedDisplay.horizontalSpeed(1.0, 0.0, 0.5);

        assertEquals(2.0, speed, 0.0001);
    }

    @Test
    void noMovementIsZero() {
        double speed = SpeedDisplay.horizontalSpeed(0.0, 0.0, 1.0);

        assertEquals(0.0, speed, 0.0001);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.SpeedDisplayTest"
```
Expected: FAIL — `SpeedDisplay` 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class SpeedDisplay implements PositionedHudFeature {
    private double lastX;
    private double lastZ;
    private boolean hasLastPosition;
    private double currentSpeed;

    public SpeedDisplay() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    // 렌더는 틱보다 자주 호출될 수 있어 매 프레임 좌표를 다시 재면 델타가 0에 가까워져 값이
    // 튄다. 틱마다 한 번만 갱신하고 render()는 그 값을 읽기만 한다.
    private void onEndTick(MinecraftClient client) {
        if (client.player == null) {
            hasLastPosition = false;
            return;
        }
        double x = client.player.getX();
        double z = client.player.getZ();
        if (hasLastPosition) {
            // 한 틱 = 1/20초, 마인크래프트 틱 레이트 고정값.
            currentSpeed = horizontalSpeed(x - lastX, z - lastZ, 1.0 / 20.0);
        }
        lastX = x;
        lastZ = z;
        hasLastPosition = true;
    }

    /** XZ 평면 거리만 잰다 — 수직(낙하·비행) 성분은 제외. */
    public static double horizontalSpeed(double dx, double dz, double deltaSeconds) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance / deltaSeconds;
    }

    @Override
    public String id() {
        return "speed";
    }

    @Override
    public String displayName() {
        return "속도 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.06, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = String.format("%.1f m/s", currentSpeed);
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
```

- [ ] **Step 4: `CubeClientModClient`에 등록**

`mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`의 `onInitializeClient()`에서 `registry.register(new FpsDisplay());` 다음 줄에 추가:

```java
        registry.register(new SpeedDisplay());
```

import에 `com.cubeclient.mod.features.SpeedDisplay;` 추가.

- [ ] **Step 5: 테스트 및 컴파일 통과 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/SpeedDisplay.java mod/src/test/java/com/cubeclient/mod/features/SpeedDisplayTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add SpeedDisplay: horizontal m/s tracked per tick"
```

---

### Task 5: `CpsDisplay`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/CpsDisplay.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/CpsDisplayTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(Task 3), `HudRenderUtil.drawScaledText`(Task 3).
- Produces: `CpsDisplay` — 클릭 기록·집계 로직은 `java.util.function.LongSupplier clockMillis`를 생성자에 주입받는 형태로 분리해 실제 1초를 기다리지 않고 테스트한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpsDisplayTest {

    @Test
    void noClicksIsZeroCps() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        assertEquals(0, display.currentCps());
    }

    @Test
    void threeClicksWithinOneSecondCountAsThree() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        now[0] = 300;
        display.recordClick();
        now[0] = 600;
        display.recordClick();
        now[0] = 900;

        assertEquals(3, display.currentCps());
    }

    // 1초 롤링 윈도우 — 1초보다 오래된 클릭은 더 이상 세지 않는다.
    @Test
    void clicksOlderThanOneSecondAgeOutOfTheWindow() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        display.recordClick();
        now[0] = 1500;
        display.recordClick();

        assertEquals(1, display.currentCps());
    }

    @Test
    void clickAtExactlyOneSecondAgoIsExcluded() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        now[0] = 1000;

        assertEquals(0, display.currentCps());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.CpsDisplayTest"
```
Expected: FAIL — `CpsDisplay` 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

public class CpsDisplay implements PositionedHudFeature {
    private static final long WINDOW_MILLIS = 1000;

    private final LongSupplier clockMillis;
    private final Deque<Long> clickTimestamps = new ArrayDeque<>();

    public CpsDisplay() {
        this(System::currentTimeMillis);
        // KeyBinding.wasPressed()는 호출할 때마다 큐를 1개씩 소모한다. 틱마다 한 번만
        // 호출하면 한 틱(1/20초)에 여러 번 눌린 빠른 연타 중 한 번만 세게 되므로, while로
        // 큐를 완전히 비우면서 소모마다 클릭을 하나씩 기록한다.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (client.options.attackKey.wasPressed()) {
                recordClick();
            }
        });
    }

    public CpsDisplay(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public void recordClick() {
        clickTimestamps.addLast(clockMillis.getAsLong());
    }

    public int currentCps() {
        long now = clockMillis.getAsLong();
        while (!clickTimestamps.isEmpty() && now - clickTimestamps.peekFirst() >= WINDOW_MILLIS) {
            clickTimestamps.pollFirst();
        }
        return clickTimestamps.size();
    }

    @Override
    public String id() {
        return "cps";
    }

    @Override
    public String displayName() {
        return "CPS 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.11, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = currentCps() + " CPS";
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
```

**참고:** 테스트용 생성자 `CpsDisplay(LongSupplier)`가 틱 리스너를 등록하지 않는 이유는, 테스트가 `ClientTickEvents`(Minecraft 클래스)를 건드리지 않고 순수 로직만 검증하게 하기 위함이다. 실제 게임에서 쓰는 기본 생성자 `CpsDisplay()`만 틱 리스너를 단다.

- [ ] **Step 4: `CubeClientModClient`에 등록**

`onInitializeClient()`에서 `registry.register(new SpeedDisplay());` 다음 줄에:

```java
        registry.register(new CpsDisplay());
```

import에 `com.cubeclient.mod.features.CpsDisplay;` 추가.

- [ ] **Step 5: 테스트 및 컴파일 통과 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/CpsDisplay.java mod/src/test/java/com/cubeclient/mod/features/CpsDisplayTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add CpsDisplay: 1-second rolling window, draining KeyBinding's press queue per tick"
```

---

### Task 6: `PerformanceDisplay`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/PerformanceDisplay.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/PerformanceDisplayTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(Task 3), `HudRenderUtil.drawScaledText`(Task 3).
- Produces: `PerformanceDisplay` — 텍스트 포맷팅 로직(`static String formatLine(double cpuLoad, long usedMemoryBytes)`)을 `public static`으로 노출해 `OperatingSystemMXBean` 없이 테스트한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceDisplayTest {

    @Test
    void formatsCpuAsPercentAndMemoryInMegabytes() {
        String line = PerformanceDisplay.formatLine(0.42, 256L * 1024 * 1024);

        assertEquals("CPU 42% | RAM 256MB", line);
    }

    // getProcessCpuLoad()는 문서화된 대로 측정이 아직 준비되지 않은 처음 몇 틱 동안 음수를
    // 돌려줄 수 있다 — 크래시 대신 "측정 중"으로 보여야 한다.
    @Test
    void negativeCpuLoadIsShownAsMeasuring() {
        String line = PerformanceDisplay.formatLine(-1.0, 100L * 1024 * 1024);

        assertEquals("CPU 측정 중 | RAM 100MB", line);
    }

    @Test
    void roundsToNearestPercentAndMegabyte() {
        String line = PerformanceDisplay.formatLine(0.336, (long) (10.6 * 1024 * 1024));

        assertEquals("CPU 34% | RAM 11MB", line);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.PerformanceDisplayTest"
```
Expected: FAIL — `PerformanceDisplay` 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.lang.management.ManagementFactory;

public class PerformanceDisplay implements PositionedHudFeature {
    private final OperatingSystemMXBean osBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    @Override
    public String id() {
        return "performance";
    }

    @Override
    public String displayName() {
        return "성능 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.16, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        double cpuLoad = osBean.getProcessCpuLoad();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        String text = formatLine(cpuLoad, usedMemory);
        HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    /** CPU는 0.0~1.0(getProcessCpuLoad 그대로) 또는 측정 전이면 음수, 메모리는 바이트. */
    public static String formatLine(double cpuLoad, long usedMemoryBytes) {
        String cpuText = cpuLoad < 0
            ? "CPU 측정 중"
            : "CPU " + Math.round(cpuLoad * 100) + "%";
        long megabytes = Math.round(usedMemoryBytes / (1024.0 * 1024.0));
        return cpuText + " | RAM " + megabytes + "MB";
    }
}
```

- [ ] **Step 4: `CubeClientModClient`에 등록**

`onInitializeClient()`에서 `registry.register(new CpsDisplay());` 다음 줄에:

```java
        registry.register(new PerformanceDisplay());
```

import에 `com.cubeclient.mod.features.PerformanceDisplay;` 추가.

- [ ] **Step 5: 테스트 및 컴파일 통과 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL. (`com.sun.management.OperatingSystemMXBean`은 JDK 표준 배포에 포함되지만 `com.sun.*` 네임스페이스라 모듈 시스템에 걸릴 수 있음 — 컴파일 에러가 나면 에러 메시지를 그대로 리뷰에 남기고, 대안으로 `java.lang.management.OperatingSystemMXBean.getSystemLoadAverage()`로 낮춰서 CPU 코어 수로 나누는 방식으로 교체한다.)

- [ ] **Step 6: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/PerformanceDisplay.java mod/src/test/java/com/cubeclient/mod/features/PerformanceDisplayTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add PerformanceDisplay: process CPU load and used heap via standard JVM APIs"
```

---

### Task 7: `ModListScreen` 스크롤

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`

**Interfaces:**
- Consumes: 없음 (B0에서 이미 만든 `ModListScreen`의 기존 필드·메서드만 사용).
- Produces: 변경 없음(외부에서 보이는 시그니처 그대로) — 내부 렌더링 동작만 스크롤 지원.

**참고:** 이 화면은 유닛 테스트 대상이 아니다(B0부터 확립된 이유 — `Screen` 렌더링·마우스 입력은 실기기 수동 검증). 이 태스크는 구현만 하고 Task 10의 수동 검증에서 실제로 카드 6개 이상(HUD 카테고리에 이제 FPS·속도·CPS·성능 4개가 있음)으로 확인한다.

- [ ] **Step 1: 스크롤 필드와 `mouseScrolled` 추가**

`mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`의 필드 선언부(`private boolean rebuildQueued;` 근처)에 추가:

```java
    private int scrollOffset;
    private static final int SCROLL_STEP = 20;
```

클래스 안 아무 곳에 메서드 추가:

```java
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = computeMaxScroll();
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * SCROLL_STEP));
        rebuildQueued = true;
        return true;
    }

    private int computeMaxScroll() {
        List<Feature> visible = registry.list(activeCategory, searchText, config.favorites());
        int cardWidth = 140;
        int gap = 12;
        int columns = Math.max(1, (width - 2 * MARGIN + gap) / (cardWidth + gap));
        int rows = (visible.size() + columns - 1) / columns;
        int cardHeight = 90;
        int totalContentHeight = rows * (cardHeight + gap);
        int visibleHeight = height - GRID_TOP - MARGIN;
        return Math.max(0, totalContentHeight - visibleHeight);
    }
```

- [ ] **Step 2: `rebuildCards()`가 `scrollOffset`을 반영하도록 수정**

`rebuildCards()` 안의 `int startY = GRID_TOP;` 줄을 다음으로 교체:

```java
        int startY = GRID_TOP - scrollOffset;
```

- [ ] **Step 3: `render()`에서 그리드 영역을 클리핑**

`render()` 메서드에서 `super.render(context, mouseX, mouseY, delta);` 호출을 다음처럼 감싼다:

```java
        context.enableScissor(0, GRID_TOP, width, height);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();
```

(주의: `context.drawCenteredTextWithShadow(textRenderer, title, ...)`로 제목을 그리는 줄은 이 scissor 블록 **밖**에 있어야 한다 — 제목은 `GRID_TOP`보다 위에 있으므로 scissor 안에 넣으면 안 보이게 된다. 기존 코드 순서상 제목 그리기가 `super.render()` 다음 줄이므로, `disableScissor()` 다음에 오도록 순서를 유지한다.)

- [ ] **Step 4: 실제 컴파일 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL. (`enableScissor`/`disableScissor`는 Task 계획 작성 중 `javap`로 실존이 확인된 메서드 — B0 코드베이스에서 사용된 적은 없으므로 여기서 첫 실사용.)

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java
git commit -m "Scroll the mod-list grid instead of letting cards run off-screen"
```

---

### Task 8: `ModListScreen`에 "HUD 조절" 탭 추가

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`

**Interfaces:**
- Consumes: `HudEditorScreen`(Task 9에서 생성 — 이 태스크가 먼저 배치되지만 컴파일은 Task 9 완료 후에야 통과한다. Step 4에서 이를 명시).
- Produces: 없음 (탭 버튼 추가만).

**주의:** 이 태스크는 `HudEditorScreen`(Task 9)을 참조하므로 Task 9보다 먼저 실행하면 컴파일이 깨진 채로 남는다. **Task 9와 순서를 바꿔 먼저 Task 9(`HudEditorScreen`)를 구현한 뒤 이 태스크를 실행한다.** (계획 문서상 번호는 8/9지만, 실행 순서는 9 → 8이다. subagent-driven-development로 실행할 때 이 순서를 지킬 것.)

- [ ] **Step 1: 탭 폭 계산에 새 탭 포함**

`mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java`의 `init()`에서 다음 줄:

```java
        int tabCount = Category.values().length + 1; // the categories, plus 전부
```

을:

```java
        int tabCount = Category.values().length + 2; // the categories, plus 전부, plus HUD 조절
```

로 바꾼다.

- [ ] **Step 2: 카테고리 루프 다음에 "HUD 조절" 탭 추가**

`init()`의 `for (Category category : Category.values()) { ... }` 루프 다음(검색창 생성 코드 이전)에 추가:

```java
        // Category enum에 넣지 않는다 — 필터가 아니라 다른 화면으로의 이동이라 의미가 다르다.
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD 조절"), b ->
            client.setScreen(new HudEditorScreen(this, registry, cachedConfig))
        ).dimensions(tabX, tabY, tabWidth, TAB_HEIGHT).build());
        tabX += tabWidth + tabGap;
```

import에 `com.cubeclient.mod.gui.HudEditorScreen`은 같은 패키지(`com.cubeclient.mod.gui`)이므로 import 불필요.

- [ ] **Step 3: 컴파일 및 테스트 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL. (Task 9가 먼저 끝나 있어야 `HudEditorScreen`이 존재해서 통과한다.)

- [ ] **Step 4: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/ModListScreen.java
git commit -m "Add a HUD 조절 tab that opens the HUD position/size editor"
```

---

### Task 9: `HudEditorScreen`

**실행 순서 주의:** 이 태스크를 Task 8보다 먼저 실행한다 (Task 8이 이 화면을 참조하므로).

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/gui/HudEditorScreen.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`, `HudPosition`(Task 1, 3), `CachedConfig`(Task 3), `FeatureRegistry`(기존).
- Produces: `HudEditorScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig)` 생성자. Task 8이 이 시그니처로 인스턴스화한다.

**참고:** 이 화면은 마우스 드래그·리사이즈·실제 렌더링이 핵심이라 B0의 `ModListScreen`/`FeatureCard`와 마찬가지로 유닛 테스트 대상이 아니다 — 실기기 수동 검증(Task 10)으로 확인한다. 구현은 명시적으로 작성한다.

- [ ] **Step 1: `HudEditorScreen` 작성**

```java
package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.registry.Feature;
import com.cubeclient.mod.registry.FeatureRegistry;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 켜진 PositionedHudFeature만 보여주고, 각각을 드래그로 옮기고 우하단 핸들로 크기를
 * 조절하는 화면. Screen을 상속하므로 싱글플레이는 일시정지 메뉴와 같은 방식으로 자연히
 * 멈춘다 — 이 화면이 새로 만드는 예외가 아니다.
 */
public class HudEditorScreen extends Screen {
    private static final int HANDLE_SIZE = 8;
    private static final int OVERLAY_MARGIN = 4;
    private static final int EXIT_BUTTON_WIDTH = 100;
    private static final int EXIT_BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final FeatureRegistry registry;
    private final CachedConfig cachedConfig;

    private final List<Entry> entries = new ArrayList<>();
    private Entry dragging;
    private boolean draggingHandle;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private double dragStartXRatio;
    private double dragStartYRatio;
    private double dragStartScale;

    public HudEditorScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig) {
        super(Text.literal("HUD 조절"));
        this.parent = parent;
        this.registry = registry;
        this.cachedConfig = cachedConfig;
    }

    @Override
    protected void init() {
        ModConfig config = cachedConfig.current();
        for (Feature feature : registry.all()) {
            if (feature instanceof PositionedHudFeature hudFeature && config.isEnabled(hudFeature.id())) {
                HudPosition position = config.positionOr(hudFeature.id(), hudFeature.defaultPosition());
                entries.add(new Entry(hudFeature, position));
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("나가기"), b -> close())
            .dimensions(width / 2 - EXIT_BUTTON_WIDTH / 2, height - EXIT_BUTTON_HEIGHT - 8,
                EXIT_BUTTON_WIDTH, EXIT_BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 배경을 덮지 않는다 — 실제 게임 화면 위 어디에 HUD가 앉는지 보면서 조절해야 한다.
        for (Entry entry : entries) {
            entry.feature.render(context, entry.position);
            drawOverlay(context, entry);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawOverlay(DrawContext context, Entry entry) {
        Bounds bounds = boundsOf(entry);
        context.drawBorder(bounds.x, bounds.y, bounds.width, bounds.height, Theme.ACCENT);
        context.fill(bounds.x + bounds.width - HANDLE_SIZE, bounds.y + bounds.height - HANDLE_SIZE,
            bounds.x + bounds.width, bounds.y + bounds.height, Theme.ACCENT);
    }

    /**
     * 오버레이 사각형의 화면 좌표. 실제 기능 렌더링 크기를 다시 계산하지 않고 고정 크기로
     * 근사한다 — 각 기능의 실제 렌더 폭을 재려면 기능마다 measureWidth() 같은 메서드가
     * 필요해지는데, B1 범위에서는 카드 하나당 텍스트 한 줄이라 고정 크기 근사로 충분하다.
     */
    private Bounds boundsOf(Entry entry) {
        int x = (int) (entry.position.xRatio() * width) - OVERLAY_MARGIN;
        int y = (int) (entry.position.yRatio() * height) - OVERLAY_MARGIN;
        int w = (int) (80 * entry.position.scale()) + OVERLAY_MARGIN * 2;
        int h = (int) (12 * entry.position.scale()) + OVERLAY_MARGIN * 2;
        return new Bounds(x, y, w, h);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (Entry entry : entries) {
            Bounds bounds = boundsOf(entry);
            boolean hitHandle = mouseX >= bounds.x + bounds.width - HANDLE_SIZE && mouseX <= bounds.x + bounds.width
                && mouseY >= bounds.y + bounds.height - HANDLE_SIZE && mouseY <= bounds.y + bounds.height;
            boolean hitBody = mouseX >= bounds.x && mouseX <= bounds.x + bounds.width
                && mouseY >= bounds.y && mouseY <= bounds.y + bounds.height;
            if (hitHandle || hitBody) {
                dragging = entry;
                draggingHandle = hitHandle;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartXRatio = entry.position.xRatio();
                dragStartYRatio = entry.position.yRatio();
                dragStartScale = entry.position.scale();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging == null) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (draggingHandle) {
            double handleDelta = (mouseX - dragStartMouseX) / 80.0;
            dragging.position = HudPosition.of(
                dragging.position.xRatio(), dragging.position.yRatio(), dragStartScale + handleDelta);
        } else {
            double newXRatio = dragStartXRatio + (mouseX - dragStartMouseX) / width;
            double newYRatio = dragStartYRatio + (mouseY - dragStartMouseY) / height;
            dragging.position = HudPosition.of(newXRatio, newYRatio, dragging.position.scale());
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            saveAll();
            dragging = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void saveAll() {
        ModConfig current = cachedConfig.current();
        java.util.Map<String, HudPosition> positions = new java.util.LinkedHashMap<>(current.positions());
        for (Entry entry : entries) {
            positions.put(entry.feature.id(), entry.position);
        }
        try {
            cachedConfig.save(new ModConfig(current.enabled(), current.favorites(), positions));
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("HUD 위치를 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private static final class Entry {
        final PositionedHudFeature feature;
        HudPosition position;

        Entry(PositionedHudFeature feature, HudPosition position) {
            this.feature = feature;
            this.position = position;
        }
    }

    private record Bounds(int x, int y, int width, int height) {}
}
```

- [ ] **Step 2: 컴파일 확인**

Run:
```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: 아직 실패한다 — `ModListScreen`이 아직 `cachedConfig` 필드를 갖고 있지 않고(Task 3에서 `ClientSettingsButton`을 거쳐 `ModListScreen`도 `CachedConfig`를 받도록 이미 고쳤어야 함 — Task 3 Step 9 확인), Task 8이 아직 실행 전이라 `ModListScreen`에서 이 클래스를 참조하는 코드가 없으므로 이 클래스 자체의 컴파일은 독립적으로 성공해야 한다. `ModListScreen`이 `cachedConfig` 필드를 갖고 있는지(Task 3 Step 9 확인) 다시 보고, 그게 되어 있다면 이 Step은 BUILD SUCCESSFUL이어야 한다.

- [ ] **Step 3: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/HudEditorScreen.java
git commit -m "Add HudEditorScreen: drag to move, corner handle to resize HUD elements"
```

이 커밋 후 Task 8을 실행한다.

---

### Task 10: 실기기 수동 검증

**Files:** 없음 (코드 변경 없음, 검증만).

**Interfaces:** 없음.

이 프로젝트는 반복적으로 유닛 테스트가 못 잡는 문제(Fabric API classpath vs mods/, 버튼 겹침, 탭 폭)를 실기기에서만 발견해왔다. B1도 같은 검증을 거친다.

- [ ] **Step 1: 모드 빌드**

```bash
cd mod && JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat build
```
Expected: BUILD SUCCESSFUL, `mod/build/libs/cubeclient-mod-0.1.0.jar` 생성.

- [ ] **Step 2: 런처로 Fabric 프로필 실행**

```bash
cd ui && CUBECLIENT_JAVA="C:/Users/Skdji/devtools/jdk17/jdk-17.0.19+10/bin/java.exe" npx electron .
```
Microsoft 로그인 → Fabric 1.21.4 버전 PLAY.

- [ ] **Step 3: 확인 항목 체크리스트**

1. 게임이 에러 없이 로드된다("호환되지 않는 모드" 같은 B0 때의 에러가 재발하지 않았는지).
2. 타이틀·일시정지 화면에서 "클라이언트 설정" → 모드 목록에 카드 4개(FPS, 속도, CPS, 성능)가 보인다.
3. 카드 6개 이상을 임시로 다 켠 상태(4개뿐이면 스크롤이 안 나올 수 있음 — 이 경우 검색창을 짧게 비활성 텍스트로 확인하는 대신, 카드가 4개인 채로 창을 세로로 작게 줄여서 스크롤이 필요한 상황을 인위적으로 만들어 확인한다)에서 마우스 휠로 스크롤되고, 화면 밖 카드가 안 보이고 클릭도 안 된다.
4. 4개 기능을 모두 켠다.
5. "HUD 조절" 탭 클릭 → 모드 목록 UI가 사라지고 HUD 4개(FPS/속도/CPS/성능)만 보인다.
6. 각 HUD 요소를 드래그해서 옮긴다 — 실시간으로 따라온다.
7. 우하단 핸들을 드래그해서 크기를 키우고 줄여본다.
8. "나가기" 버튼 클릭 → 모드 목록 화면으로 돌아간다.
9. 게임 완전히 재시작 → HUD 요소들이 옮긴 위치·크기 그대로 유지된다(설정 파일 왕복 확인).
10. 창 크기를 다르게 바꿔서 재실행 → 비율 기반이라 상대 위치가 유지되는지 확인(예: 화면 좌상단 근처였던 요소가 창을 늘려도 여전히 좌상단 근처에 있는지).
11. 속도 표시가 실제로 걸을 때 오르내리는지, 가만히 있으면 0에 가까운지 확인.
12. CPS 표시가 좌클릭 연타 시 올라가고 멈추면 1초 안에 0으로 내려가는지 확인.
13. 성능 표시에 CPU%·RAM(MB)이 표시되고 음수나 이상한 값이 아닌지 확인.
14. 멀티플레이(또는 서버 접속 상태)에서 "HUD 조절" 화면을 열어도 월드가 계속 진행되는지 확인(설계대로 — 손대지 않은 바닐라 동작).

- [ ] **Step 4: 문제 발견 시**

`.superpowers/sdd/progress.md`에 발견된 버그와 원인을 기록하고 고친 뒤 재검증한다 — B0에서 확립된 패턴 그대로.

- [ ] **Step 5: 통과 후 기록**

`.superpowers/sdd/progress.md`에 "B1 COMPLETE" 절을 추가하고, 커밋:

```bash
git add .superpowers/sdd/progress.md
git commit -m "Record B1 manual verification results"
```
