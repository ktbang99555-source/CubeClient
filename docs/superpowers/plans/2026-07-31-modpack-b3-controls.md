# Sub-project B3: 조작 (Toggle Sprint, C키 Zoom) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모드 목록 화면의 조작(`Category.CONTROL`) 카테고리에 두 기능을 추가한다 — 바닐라 달리기 키를 눌렀다 떼면 계속 유지되는 Toggle Sprint, C키를 누르고 있는 동안 FOV·마우스 감도를 고정 4배로 줄이는 Zoom.

**Architecture:** 새 프레임워크 없음, B0의 `FeatureRegistry`/`Feature`/`Category.CONTROL`을 그대로 재사용한다. 둘 다 화면 요소가 없어 `PositionedHudFeature`가 아니라 `Feature`를 직접 구현하고, 각자 `ClientTickEvents.END_CLIENT_TICK` 리스너를 등록해 동작한다. 두 기능 다 **자기 자신의 on/off 상태를 스스로 확인**해야 한다 — 기존 `PositionedHudFeature`는 `CubeClientModClient`의 중앙 렌더 루프가 `config.isEnabled(id())`를 대신 확인해주지만, 화면이 없는 `Feature`는 그런 중앙 디스패치가 없으므로 각 기능의 틱 리스너 안에서 직접 `CachedConfig`를 참조해 켜짐 여부를 확인한다(이번 계획에서 처음 나오는 패턴).

**Tech Stack:** Fabric Loom 1.10.2, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader `0.19.3`, Fabric API `0.119.4+1.21.4`(`fabric-key-binding-api-v1` 포함), JDK 21, JUnit 5.

## Global Constraints

- Loom/Yarn/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `gradle.properties`만 참조한다.
- Mixin을 쓰지 않는다 — 이번 두 기능도 예외 없음. (줌 배율 조절을 마우스 휠로 하려던 계획은 이 제약 때문에 포기했다 — 아래 참고.)
- 색상은 해당 없음(HUD 요소 없음).
- 토글/설정 변경은 즉시(다음 틱부터) 반영되어야 한다.
- 알 수 없는 설정 id는 무시한다(에러 아님).
- vanilla 키를 폴링할 땐 `wasPressed()`가 아니라 `isPressed()` + 직접 edge 감지를 쓴다. 단, B3의 두 기능은 CPS/Combo처럼 "횟수"를 정확히 세야 하는 게 아니라 단발성 누름/뗌만 놓치지 않으면 되므로, B1/B2의 "프레임 단위로 폴링"까지는 필요 없다 — 틱 단위(`END_CLIENT_TICK`)로 충분하다.
- 모드 프로젝트 빌드는 `JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10"`로 `./gradlew.bat`를 실행한다(중첩 디렉터리 정확히 지정).

## 검증된 API 시그니처 (추측 아님 — `javap`로 실제 jar를 직접 뜯어 확인함)

```java
// net.minecraft.client.option.GameOptions
public final net.minecraft.client.option.KeyBinding sprintKey;
public net.minecraft.client.option.SimpleOption<java.lang.Integer> getFov();
public net.minecraft.client.option.SimpleOption<java.lang.Double> getMouseSensitivity();

// net.minecraft.client.option.SimpleOption<T>
public T getValue();
public void setValue(T);

// net.minecraft.entity.Entity (PlayerEntity가 상속)
public boolean isSprinting();
public void setSprinting(boolean);

// net.minecraft.client.option.KeyBinding
public net.minecraft.client.option.KeyBinding(String translationKey, InputUtil.Type type, int code, String category);
public boolean isPressed();

// net.minecraft.client.util.InputUtil$Type — enum 상수: KEYSYM, SCANCODE, MOUSE

// net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper (fabric-key-binding-api-v1)
public static KeyBinding registerKeyBinding(KeyBinding);

// org.lwjgl.glfw.GLFW
public static final int GLFW_KEY_C = 67;

// net.minecraft.client.MinecraftClient
public net.minecraft.client.gui.screen.Screen currentScreen; // 공개 필드, 화면 없으면 null

// net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
Event<ClientTickEvents.EndTick> END_CLIENT_TICK; // B1/B2에서 이미 쓰던 것, 재확인 불필요
```

**확인 안 된 채 넘어가는 것(런타임 동작이라 스크래치 컴파일로 못 잡음, 실기기 검증 대상)**: `ToggleSprint`가 `END_CLIENT_TICK`에서 `setSprinting(true)`를 강제해도 바닐라 자신의 스프린트 판정을 실제로 이기는지(실행 순서). Task 3에서 확인한다.

**마우스 휠로 줌 배율을 조절하는 기능은 이 계획에 없다** — 브레인스토밍 직후 확인 결과 `Mouse.onMouseScroll`이 private이고 Fabric API 전체에 게임플레이 중 스크롤을 읽는 공개 이벤트가 없어서, 믹스인 없이는 불가능했다(스펙의 "범위 밖" 참고, 사용자 확정).

---

### Task 1: `ToggleSprint`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/ToggleSprint.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/ToggleSprintTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `Feature`(B0), `Category.CONTROL`(B0), `CachedConfig`(B1, 이미 `CubeClientModClient.onInitializeClient()`의 로컬 변수로 존재) — `CachedConfig.current()`가 반환하는 `ModConfig.isEnabled(String)`을 그대로 쓴다.
- Produces: `ToggleSprint` — 순수 테스트 가능한 `static boolean nextToggleState(boolean current, boolean isDown, boolean wasDown)`. 이후 태스크가 참조하지 않음.

바닐라의 기존 달리기 키(`client.options.sprintKey`)를 그대로 읽는다 — 새 키바인딩을 만들지 않는다. 한 번 눌렀다 떼면 `sprintOn`이 켜지고, 매 틱 `player.setSprinting(true)`를 강제해서 물리적으로 키를 안 누르고 있어도 계속 달린다. 다시 누르면 꺼진다. 기능이 모드 목록에서 꺼져 있으면 아무 것도 안 하고, 상태도 초기화한다(꺼졌다 켜질 때 예전 토글 상태가 남아있지 않게).

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleSprintTest {

    @Test
    void risingEdgeTurnsOnFromOff() {
        assertEquals(true, ToggleSprint.nextToggleState(false, true, false));
    }

    @Test
    void risingEdgeTurnsOffFromOn() {
        assertEquals(false, ToggleSprint.nextToggleState(true, true, false));
    }

    // 키를 누른 채로 여러 틱이 지나가도(눌림 유지, edge 아님) 상태가 그대로여야 한다 —
    // 안 그러면 누르고 있는 동안 매 틱 토글되어 사실상 즉시 꺼진다.
    @Test
    void heldDownWithoutEdgeDoesNotToggle() {
        assertEquals(false, ToggleSprint.nextToggleState(false, true, true));
    }

    @Test
    void notPressedDoesNotChangeState() {
        assertEquals(true, ToggleSprint.nextToggleState(true, false, false));
    }

    // 뗄 때(하강 edge)는 토글하지 않는다 — 토글은 누르는 순간에만 일어난다.
    @Test
    void releaseEdgeDoesNotToggle() {
        assertEquals(true, ToggleSprint.nextToggleState(true, false, true));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.ToggleSprintTest"
```
Expected: FAIL — `ToggleSprint` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class ToggleSprint implements Feature {
    private final CachedConfig cachedConfig;
    private boolean sprintOn;
    private boolean sprintKeyWasDown;

    public ToggleSprint(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        if (!cachedConfig.current().isEnabled(id())) {
            // 꺼져 있으면 아무 것도 강제하지 않고, 다음에 켜졌을 때 엉뚱한 edge가 안 잡히게
            // 상태를 초기화해둔다.
            sprintOn = false;
            sprintKeyWasDown = false;
            return;
        }

        boolean isDown = client.options.sprintKey.isPressed();
        sprintOn = nextToggleState(sprintOn, isDown, sprintKeyWasDown);
        sprintKeyWasDown = isDown;

        if (sprintOn) {
            client.player.setSprinting(true);
        }
    }

    /** 누르는 순간(rising edge)에만 토글한다 — 누르고 있는 동안이나 뗄 때는 상태 유지. */
    static boolean nextToggleState(boolean current, boolean isDown, boolean wasDown) {
        if (isDown && !wasDown) {
            return !current;
        }
        return current;
    }

    @Override
    public String id() {
        return "toggle_sprint";
    }

    @Override
    public String displayName() {
        return "Toggle Sprint";
    }

    @Override
    public Category category() {
        return Category.CONTROL;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.ToggleSprintTest"
```
Expected: PASS, 5개 테스트 전부.

- [ ] **Step 5: `CubeClientModClient`에 등록**

`mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`의 `onInitializeClient()`에서 `registry.register(new ComboCounter());` 다음 줄에 추가:

```java
        registry.register(new ToggleSprint(cachedConfig));
```

import에 `com.cubeclient.mod.features.ToggleSprint;` 추가. (`cachedConfig`는 이미 그 메서드 안에 로컬 변수로 존재 — 새로 만들 필요 없음.)

- [ ] **Step 6: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/ToggleSprint.java mod/src/test/java/com/cubeclient/mod/features/ToggleSprintTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add ToggleSprint: press-to-toggle sprint using vanilla's own sprint key"
```

---

### Task 2: `ZoomKey`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/ZoomKey.java`
- Create: `mod/src/main/resources/assets/cubeclient/lang/en_us.json`
- Create: `mod/src/main/resources/assets/cubeclient/lang/ko_kr.json`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `Feature`(B0), `Category.CONTROL`(B0), `CachedConfig`(B1, 이미 로컬 변수).
- Produces: `ZoomKey`. 이후 태스크가 참조하지 않음.

이 모드 최초의 커스텀 키바인딩(기본 키: C). 누르는 동안 FOV·마우스 감도를 고정 4배로 줄이고, 떼거나 기능이 꺼지거나 화면(일시정지 메뉴 등)이 열리면 즉시 원래 값으로 복원한다. `MinecraftClient.currentScreen != null`을 명시적으로 확인해서 화면이 열리는 순간 바로 복원하는 이유는, 화면이 열려 있는 동안 `KeyBinding.isPressed()`가 실제 물리 키 상태를 계속 반영하는지 여부가 불확실하기 때문 — 그 불확실성에 기대지 않고 화면 유무로 직접 판단하면 "줌에 걸려서 안 풀리는" 버그를 구조적으로 막을 수 있다.

**테스트 없음 — 의도된 것**: `render()` 없는 `Feature`지만 로직 전체가 `MinecraftClient.options`/`KeyBinding`/`currentScreen`에 직접 묶여 있고, 저장했다 복원하는 것 외의 순수 분기가 없다(배율은 상수 4.0 고정, 조절 로직 자체가 없음). B0~B2의 다른 Minecraft-결합 코드와 같은 성격 — 실기기 수동 검증 대상(Task 3).

- [ ] **Step 1: lang 리소스 작성**

`mod/src/main/resources/assets/cubeclient/lang/en_us.json`:
```json
{
  "key.categories.cubeclient": "CubeClient",
  "key.cubeclient.zoom": "Zoom"
}
```

`mod/src/main/resources/assets/cubeclient/lang/ko_kr.json`:
```json
{
  "key.categories.cubeclient": "CubeClient",
  "key.cubeclient.zoom": "확대(Zoom)"
}
```

(이 파일들이 없어도 기능은 동작하지만, 바닐라 설정 → 조작 화면에 키 이름이 번역 키 원문 그대로 뜨는 대신 제대로 된 이름으로 표시된다.)

- [ ] **Step 2: 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ZoomKey implements Feature {
    private static final double ZOOM_FACTOR = 4.0;

    private final CachedConfig cachedConfig;
    private final KeyBinding zoomKey;
    private boolean zoomed;
    private int originalFov;
    private double originalSensitivity;

    public ZoomKey(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
        this.zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cubeclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.cubeclient"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            restoreIfZoomed(client);
            return;
        }

        boolean enabled = cachedConfig.current().isEnabled(id());
        boolean screenOpen = client.currentScreen != null;
        boolean shouldZoom = enabled && !screenOpen && zoomKey.isPressed();

        if (shouldZoom && !zoomed) {
            SimpleOption<Integer> fov = client.options.getFov();
            SimpleOption<Double> sensitivity = client.options.getMouseSensitivity();
            originalFov = fov.getValue();
            originalSensitivity = sensitivity.getValue();
            fov.setValue((int) (originalFov / ZOOM_FACTOR));
            sensitivity.setValue(originalSensitivity / ZOOM_FACTOR);
            zoomed = true;
        } else if (!shouldZoom) {
            restoreIfZoomed(client);
        }
    }

    private void restoreIfZoomed(MinecraftClient client) {
        if (!zoomed) {
            return;
        }
        client.options.getFov().setValue(originalFov);
        client.options.getMouseSensitivity().setValue(originalSensitivity);
        zoomed = false;
    }

    @Override
    public String id() {
        return "zoom";
    }

    @Override
    public String displayName() {
        return "Zoom (C키)";
    }

    @Override
    public Category category() {
        return Category.CONTROL;
    }
}
```

- [ ] **Step 3: `CubeClientModClient`에 등록**

`onInitializeClient()`에서 `registry.register(new ToggleSprint(cachedConfig));` 다음 줄에:

```java
        registry.register(new ZoomKey(cachedConfig));
```

import에 `com.cubeclient.mod.features.ZoomKey;` 추가.

- [ ] **Step 4: 컴파일 및 전체 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/ZoomKey.java mod/src/main/resources/assets/cubeclient/lang/en_us.json mod/src/main/resources/assets/cubeclient/lang/ko_kr.json mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add ZoomKey: hold C for fixed 4x FOV+sensitivity zoom, restores on release/disable/screen-open"
```

---

### Task 3: 실기기 수동 검증

**Files:** 없음 — 코드 변경 없는 검증 태스크.

**Interfaces:** 없음.

빌드된 모드를 실제 Minecraft 인스턴스에 붙여 확인한다. Task 1~2는 컴파일·유닛 테스트만 통과했을 뿐, 가장 중요한 위험 요소(`ToggleSprint`가 바닐라의 자체 스프린트 판정을 실제로 이기는지)는 실기기가 아니면 확인할 방법이 없다.

- [ ] **Step 1: 모드 빌드**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat build
```
Expected: BUILD SUCCESSFUL. 사용자에게 `runClient` 태스크 실행 또는 빌드된 모드를 붙인 인스턴스 실행을 안내한다.

- [ ] **Step 2: Toggle Sprint 확인**

달리기 키를 한 번 눌렀다 떼도 계속 달리는지 / 다시 누르면 멈추는지 / 허기가 부족하거나 웅크리는 중처럼 바닐라가 스프린트를 막는 상황에서 억지로 뚫지 않고 자연스럽게 막히는지 / 모드 목록에서 기능을 끄면 즉시 멈추는지 확인.

- [ ] **Step 3: C키 Zoom 확인**

C키를 누르는 동안 화면이 확대(4배)되고 감도도 같이 줄어 정밀 조준이 되는지 / 떼면 즉시 원래대로 돌아오는지 / 마우스 휠은 줌 중에도 평소처럼 단축슬롯을 전환하는지(줌 배율에는 영향 없어야 함) / 일시정지 메뉴나 인벤토리 등 다른 화면을 줌 도중 열어도 FOV·감도가 걸리지 않고 즉시 복원되는지 / 모드 목록에서 기능을 끄면(줌 중이라도) 즉시 복원되는지.

- [ ] **Step 4: 문제 발견 시 대응**

실기기에서 스펙과 다르게 동작하는 부분이 있으면(특히 `ToggleSprint`가 안 먹히는 경우 — 실행 순서 가정이 틀렸을 가능성) 해당 기능 파일만 수정하고 관련 유닛 테스트(있는 경우)를 다시 통과시킨 뒤 별도 커밋으로 남긴다. `ToggleSprint`가 정말 안 먹히면(우리 리스너가 바닐라보다 먼저 실행되는 경우), 대안으로 매 틱 초반이 아니라 별도의 늦게 실행되는 훅을 찾거나, `WorldRenderEvents.START` 같은 렌더 직전 훅으로 옮기는 것을 검토한다 — 이 계획의 Task 1 커밋은 건드리지 않는다.

---

## 자체 검토 결과

- **스펙 커버리지**: Toggle Sprint(Task 1), C키 Zoom(Task 2), 실기기 검증 항목(Task 3)이 스펙의 아키텍처·오류 처리·테스트 절과 1:1 대응. 스펙의 "범위 밖" 항목(Toggle Sneak, 두 기능 통합, 시네마틱 이펙트, 줌 배율 조절)은 어떤 태스크에도 없음 — 의도대로.
- **플레이스홀더 스캔**: "TBD"/"나중에" 류 표현 없음. `ZoomKey`(Task 2)에 유닛 테스트가 없는 건 누락이 아니라 스펙·태스크 설명이 이미 근거를 명시한 의도적 결정.
- **타입 일관성**: `ToggleSprint.nextToggleState(boolean, boolean, boolean)` 시그니처가 테스트와 구현에서 동일. `id()` 문자열(`toggle_sprint`, `zoom`)이 기존 8개 기능(`fps`, `speed`, `cps`, `performance`, `resource_pack`, `durability`, `ping`, `combo_counter`)과 겹치지 않음 — 겹치면 `FeatureRegistry.register()`가 즉시 예외를 던지므로 Task 1/2 컴파일·테스트 단계에서 바로 드러난다. 두 기능 다 생성자가 `CachedConfig`를 받는 새 패턴이지만, `CubeClientModClient.onInitializeClient()`에 이미 존재하는 로컬 변수를 그대로 넘기는 것뿐이라 다른 기능의 등록부에 영향 없음.
