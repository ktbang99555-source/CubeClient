# Sub-project B2: 나머지 HUD 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** B1의 HUD 프레임워크(`PositionedHudFeature`/`HudRenderUtil`/`HudEditorScreen`)를 그대로 재사용해서 리소스팩 표시, 갑옷·도구 내구도 표시, 서버 접속 시 핑 표시, Combo Counter 네 가지 HUD 기능을 추가한다.

**Architecture:** 새 프레임워크는 없다. `HudRenderUtil.drawScaledText`/`TextDrawer`를 내용 중립적인 이름(`drawScaled`/`ScaledDrawer`)으로 순수 리네임해서 아이템 아이콘도 텍스트와 같은 스케일 콜백 안에서 그릴 수 있게 한 뒤, 네 기능을 각각 `PositionedHudFeature`로 구현해 `FeatureRegistry`에 등록한다. `ComboCounter`만 믹스인 금지 제약 안에서 `MinecraftClient.targetedEntity`/`LivingEntity.hurtTime` 같은 공개 필드를 폴링해 히트를 감지하는 자체 틱 리스너를 추가로 등록한다.

**Tech Stack:** Fabric Loom 1.10.2, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader `0.19.3`, Fabric API `0.119.4+1.21.4`, JDK 21, JUnit 5.

## Global Constraints

- Loom/Yarn/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `gradle.properties`만 참조한다.
- Mixin을 쓰지 않는다 — `ComboCounter`도 예외 없음, 공개 필드 폴링으로 감지한다.
- 색상은 `com.cubeclient.mod.gui.Theme` 상수만 쓴다 (`GROUND`, `PANEL`, `BORDER`, `TEXT`, `MUTED`, `ACCENT`, `WARNING`). B2는 전부 `Theme.TEXT`만 쓴다 — 내구도 경고색 등은 범위 밖.
- 토글/설정 변경은 즉시(다음 프레임부터) 반영되어야 한다.
- 알 수 없는 설정 id는 무시한다(에러 아님).
- 모드 프로젝트 빌드는 `JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10"`로 `./gradlew.bat`를 실행한다(중첩 디렉터리 정확히 지정).
- 리소스팩/내구도/핑/콤보 카운터의 표시 방식은 스펙에서 확정된 그대로다: 내구도는 남은 값만(퍼센트나 `n/max` 아님), 핑은 싱글플레이에서 자동 숨김, 콤보는 몬스터+플레이어 공격 전부 카운트하며 3초 무명중 또는 피격 시 즉시 리셋.

## 검증된 API 시그니처 (추측 아님 — javap로 실제 jar 확인 + 스크래치 클래스 실컴파일로 재검증됨)

```java
// net.minecraft.entity.LivingEntity
public int hurtTime; // 공개 필드, 데미지 받으면 서버 동기화로 0→양수
public abstract net.minecraft.item.ItemStack getEquippedStack(net.minecraft.entity.EquipmentSlot);

// net.minecraft.entity.EquipmentSlot — enum 상수: HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND

// net.minecraft.item.ItemStack
public boolean isEmpty();
public boolean isDamageable();
public int getDamage();
public int getMaxDamage();

// net.minecraft.client.gui.DrawContext
public void drawItem(net.minecraft.item.ItemStack, int, int);

// net.minecraft.client.MinecraftClient
public net.minecraft.entity.Entity targetedEntity; // 공개 필드, 타입은 Entity — LivingEntity 아님, instanceof로 좁혀야 함
public net.minecraft.client.network.ClientPlayNetworkHandler getNetworkHandler();
public boolean isInSingleplayer();
public net.minecraft.resource.ResourcePackManager getResourcePackManager();

// net.minecraft.resource.ResourcePackManager
public java.util.Collection<net.minecraft.resource.ResourcePackProfile> getEnabledProfiles();

// net.minecraft.resource.ResourcePackProfile
public net.minecraft.text.Text getDisplayName();
public java.lang.String getId();

// net.minecraft.client.network.ClientPlayNetworkHandler
public net.minecraft.client.network.PlayerListEntry getPlayerListEntry(java.util.UUID);

// net.minecraft.client.network.PlayerListEntry
public int getLatency();

// net.minecraft.entity.Entity
public java.util.UUID getUuid();
public final boolean isRemoved();
```

`GameOptions.attackKey`/`KeyBinding.isPressed()`는 이미 `CpsDisplay`(B1)에서 검증·사용 중인 패턴 — `ComboCounter`가 그대로 재사용한다.

---

### Task 1: `HudRenderUtil` 리네임 (`drawScaledText`→`drawScaled`, `TextDrawer`→`ScaledDrawer`)

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/HudRenderUtil.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/features/FpsDisplay.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/features/SpeedDisplay.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/features/CpsDisplay.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/features/PerformanceDisplay.java`

**Interfaces:**
- Consumes: 없음 (기존 `HudRenderUtil.drawScaledText`/`HudRenderUtil.TextDrawer`를 그대로 대체).
- Produces: `HudRenderUtil.drawScaled(DrawContext, HudPosition, HudRenderUtil.ScaledDrawer)` — 콜백 시그니처(`(DrawContext, int x, int y) -> void`)는 기존과 동일. Task 2/3/4/5가 이 이름을 쓴다.

로직 변경 없는 순수 리네임이라 새 테스트는 필요 없다 — 기존 테스트 스위트가 4개 기능의 동작을 이미 커버하므로, 리네임 후 전체 테스트가 그대로 통과하는 것 자체가 검증이다.

- [ ] **Step 1: `HudRenderUtil` 리네임**

`mod/src/main/java/com/cubeclient/mod/gui/HudRenderUtil.java` 전체를 다음으로 교체:

```java
package com.cubeclient.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * PositionedHudFeature 구현체가 공유하는 렌더링 절차: 비율 좌표를 화면 배율 기준 픽셀로
 * 바꾸고, 행렬 스택을 push/scale/pop으로 감싸 배율을 적용한 뒤, 그 안에서 실제 콘텐츠를
 * 그린다. 콜백은 텍스트뿐 아니라 아이템 아이콘(DurabilityDisplay)도 그리므로 이름과
 * 인터페이스를 내용 중립적으로 둔다 — 시그니처 자체는 원래도 텍스트 전용이 아니었다.
 */
public final class HudRenderUtil {
    private HudRenderUtil() {}

    public static void drawScaled(DrawContext context, HudPosition pos, ScaledDrawer drawer) {
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
    public interface ScaledDrawer {
        void draw(DrawContext context, int x, int y);
    }
}
```

- [ ] **Step 2: 4개 호출부를 새 이름으로 고침**

`FpsDisplay.java`, `SpeedDisplay.java`, `CpsDisplay.java`, `PerformanceDisplay.java` 네 파일 모두에서 동일한 치환을 한다:

```java
// 기존
HudRenderUtil.drawScaledText(context, pos, (ctx, x, y) ->
```
을
```java
// 변경 후
HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
```
로 바꾼다. 각 파일에 이 호출은 정확히 한 번씩만 있다 — 다른 코드 변경 없음.

- [ ] **Step 3: 전체 테스트로 회귀 없음 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL — 기존 `SpeedDisplayTest`, `CpsDisplayTest`, `PerformanceDisplayTest` 등 전부 그대로 통과(이 네 기능의 동작은 안 바뀌었으므로).

- [ ] **Step 4: 컴파일 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/gui/HudRenderUtil.java mod/src/main/java/com/cubeclient/mod/features/FpsDisplay.java mod/src/main/java/com/cubeclient/mod/features/SpeedDisplay.java mod/src/main/java/com/cubeclient/mod/features/CpsDisplay.java mod/src/main/java/com/cubeclient/mod/features/PerformanceDisplay.java
git commit -m "Rename HudRenderUtil.drawScaledText to drawScaled: callback isn't text-only anymore"
```

---

### Task 2: `ResourcePackDisplay`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/ResourcePackDisplay.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/ResourcePackDisplayTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(B1), `HudRenderUtil.drawScaled`(Task 1).
- Produces: `ResourcePackDisplay` — 순수 테스트 가능한 `public static String formatLine(java.util.List<String> displayNames)` (바닐라 제외, 이미 필터링된 이름 목록을 받음). 이후 태스크가 참조하지 않음.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourcePackDisplayTest {

    @Test
    void noEnabledPacksShowsNoneMessage() {
        String line = ResourcePackDisplay.formatLine(List.of());

        assertEquals("리소스팩 없음", line);
    }

    @Test
    void singlePackShowsItsName() {
        String line = ResourcePackDisplay.formatLine(List.of("Faithful"));

        assertEquals("Faithful", line);
    }

    @Test
    void multiplePacksAreCommaJoined() {
        String line = ResourcePackDisplay.formatLine(List.of("Faithful", "Sound Pack"));

        assertEquals("Faithful, Sound Pack", line);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.ResourcePackDisplayTest"
```
Expected: FAIL — `ResourcePackDisplay` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourcePackProfile;

import java.util.Collection;
import java.util.List;

public class ResourcePackDisplay implements PositionedHudFeature {
    @Override
    public String id() {
        return "resource_pack";
    }

    @Override
    public String displayName() {
        return "리소스팩 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.21, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = formatLine(enabledDisplayNames(client.getResourcePackManager().getEnabledProfiles()));
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    // 바닐라 기본 팩("vanilla")은 항상 켜져 있어 목록에 포함되지만, 사용자 입장에서는 "리소스팩을
    // 적용 중"이라 여기지 않으므로 표시에서 제외한다.
    private static List<String> enabledDisplayNames(Collection<ResourcePackProfile> enabled) {
        return enabled.stream()
            .filter(profile -> !"vanilla".equals(profile.getId()))
            .map(profile -> profile.getDisplayName().getString())
            .toList();
    }

    /** 바닐라 제외까지 끝난 이름 목록을 받는 순수 포맷팅 — Minecraft 클래스 없이 테스트한다. */
    public static String formatLine(List<String> displayNames) {
        if (displayNames.isEmpty()) {
            return "리소스팩 없음";
        }
        return String.join(", ", displayNames);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.ResourcePackDisplayTest"
```
Expected: PASS, 3개 테스트 전부.

- [ ] **Step 5: `CubeClientModClient`에 등록**

`mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`의 `onInitializeClient()`에서 `registry.register(new PerformanceDisplay());` 다음 줄에 추가:

```java
        registry.register(new ResourcePackDisplay());
```

import에 `com.cubeclient.mod.features.ResourcePackDisplay;` 추가.

- [ ] **Step 6: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/ResourcePackDisplay.java mod/src/test/java/com/cubeclient/mod/features/ResourcePackDisplayTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add ResourcePackDisplay: show enabled resource pack names, excluding vanilla"
```

---

### Task 3: `DurabilityDisplay`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/DurabilityDisplay.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(B1), `HudRenderUtil.drawScaled`(Task 1).
- Produces: `DurabilityDisplay`. 이후 태스크가 참조하지 않음.

**테스트 없음 — 스펙에서 이미 확정된 사항**: `render()` 전체가 `ItemStack`/`EquipmentSlot`/`DrawContext` 등 Minecraft 클래스에 직접 묶여 있고, 남은 내구도 계산(`getMaxDamage() - getDamage()`)이 한 줄짜리 뺄셈이라 별도 순수 함수로 뽑아도 테스트할 분기가 없다. B0~B1의 다른 화면 렌더링 코드와 같은 성격 — 실기기 수동 검증 대상(Task 6).

- [ ] **Step 1: 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.List;

public class DurabilityDisplay implements PositionedHudFeature {
    @Override
    public String id() {
        return "durability";
    }

    @Override
    public String displayName() {
        return "내구도 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.26, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        // 배열 인덱스(PlayerInventory.getArmorStack(int))를 추측해서 쓰지 않는다 — 이름 기반인
        // getEquippedStack(EquipmentSlot)을 써서 순서를 오해할 여지 자체를 없앤다.
        List<ItemStack> slots = List.of(
            client.player.getEquippedStack(EquipmentSlot.HEAD),
            client.player.getEquippedStack(EquipmentSlot.CHEST),
            client.player.getEquippedStack(EquipmentSlot.LEGS),
            client.player.getEquippedStack(EquipmentSlot.FEET),
            client.player.getEquippedStack(EquipmentSlot.MAINHAND)
        );
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) -> {
            int row = 0;
            for (ItemStack stack : slots) {
                if (stack.isEmpty() || !stack.isDamageable()) {
                    continue;
                }
                int rowY = y + row * 20;
                ctx.drawItem(stack, x, rowY);
                int remaining = stack.getMaxDamage() - stack.getDamage();
                ctx.drawTextWithShadow(client.textRenderer, String.valueOf(remaining), x + 20, rowY + 4, Theme.TEXT);
                row++;
            }
        });
    }
}
```

- [ ] **Step 2: `CubeClientModClient`에 등록**

`onInitializeClient()`에서 `registry.register(new ResourcePackDisplay());` 다음 줄에:

```java
        registry.register(new DurabilityDisplay());
```

import에 `com.cubeclient.mod.features.DurabilityDisplay;` 추가.

- [ ] **Step 3: 컴파일 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/DurabilityDisplay.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add DurabilityDisplay: vertical icon+remaining-durability rows for armor and mainhand tool"
```

---

### Task 4: `PingDisplay`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/PingDisplay.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/PingDisplayTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(B1), `HudRenderUtil.drawScaled`(Task 1).
- Produces: `PingDisplay` — 순수 테스트 가능한 `public static String formatLine(int latencyMillis)`. 이후 태스크가 참조하지 않음.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingDisplayTest {

    @Test
    void formatsLatencyWithMsSuffix() {
        assertEquals("42ms", PingDisplay.formatLine(42));
    }

    @Test
    void zeroLatencyIsStillShown() {
        assertEquals("0ms", PingDisplay.formatLine(0));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.PingDisplayTest"
```
Expected: FAIL — `PingDisplay` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

public class PingDisplay implements PositionedHudFeature {
    @Override
    public String id() {
        return "ping";
    }

    @Override
    public String displayName() {
        return "핑 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.31, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        // 싱글플레이는 핑 개념이 없으므로 자동으로 숨긴다(사용자 확정) — 토글 자체는 켠 채로
        // 두고, 렌더링만 조건부로 건너뛴다.
        if (client.isInSingleplayer() || client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry == null) {
            // 접속 직후 탭 목록이 아직 안 왔을 수 있는 극초반 프레임 — 에러 아님, 다음 프레임에
            // 자연히 채워진다.
            return;
        }
        String text = formatLine(entry.getLatency());
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    public static String formatLine(int latencyMillis) {
        return latencyMillis + "ms";
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.PingDisplayTest"
```
Expected: PASS, 2개 테스트 전부.

- [ ] **Step 5: `CubeClientModClient`에 등록**

`onInitializeClient()`에서 `registry.register(new DurabilityDisplay());` 다음 줄에:

```java
        registry.register(new PingDisplay());
```

import에 `com.cubeclient.mod.features.PingDisplay;` 추가.

- [ ] **Step 6: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/PingDisplay.java mod/src/test/java/com/cubeclient/mod/features/PingDisplayTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add PingDisplay: current server latency, auto-hidden in singleplayer"
```

---

### Task 5: `ComboCounter`

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/ComboCounter.java`
- Create: `mod/src/test/java/com/cubeclient/mod/features/ComboCounterTest.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: `PositionedHudFeature`(B1), `HudRenderUtil.drawScaled`(Task 1).
- Produces: `ComboCounter` — 순수 테스트 가능한 `static boolean shouldResetForTimeout(long now, long lastHitAtMillis)`. 이후 태스크가 참조하지 않음. B2에서 가장 리스크가 큰 기능이므로 Task 6의 실기기 검증에서 PvE·PvP 둘 다 반드시 확인한다.

가장 복잡한 기능 — 서버가 데미지를 판정하므로 클라이언트 모드는 "맞았다"를 직접 알 수 없다. 믹스인 금지 제약 안에서 공개 필드(`MinecraftClient.targetedEntity`, `LivingEntity.hurtTime`)만 폴링해 감지한다. 공격키를 누른 순간의 조준 대상을 "대기 중인 스윙"으로 기록해두고, 몇 틱 안에 그 대상의 `hurtTime`이 0→양수로 바뀌면 명중으로 판정한다(네트워크 왕복 지연 감안 감지 창). 창 안에 안 바뀌면 그냥 빗나간 것으로 보고 버린다 — 리셋 사유 아님.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboCounterTest {

    @Test
    void doesNotResetBeforeThreeSecondsPass() {
        assertFalse(ComboCounter.shouldResetForTimeout(2999, 0));
    }

    @Test
    void resetsAtExactlyThreeSeconds() {
        assertTrue(ComboCounter.shouldResetForTimeout(3000, 0));
    }

    @Test
    void resetsPastThreeSeconds() {
        assertTrue(ComboCounter.shouldResetForTimeout(3001, 0));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.ComboCounterTest"
```
Expected: FAIL — `ComboCounter` 클래스 없음.

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
import net.minecraft.entity.LivingEntity;

import java.util.function.LongSupplier;

public class ComboCounter implements PositionedHudFeature {
    private static final long RESET_AFTER_MILLIS = 3000;
    private static final int SWING_WINDOW_TICKS = 10; // 네트워크 왕복 감안 여유

    private final LongSupplier clockMillis;
    private int combo;
    private long lastHitAtMillis;
    private boolean attackKeyWasDown;
    private LivingEntity pendingTarget;
    private int pendingSwingTicksLeft;
    private int lastPendingTargetHurtTime;
    private int lastOwnHurtTime;

    public ComboCounter() {
        this(System::currentTimeMillis);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    ComboCounter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        boolean isDown = client.options.attackKey.isPressed();
        if (isDown && !attackKeyWasDown && client.targetedEntity instanceof LivingEntity target) {
            pendingTarget = target;
            pendingSwingTicksLeft = SWING_WINDOW_TICKS;
            lastPendingTargetHurtTime = target.hurtTime;
        }
        attackKeyWasDown = isDown;

        if (pendingTarget != null) {
            if (pendingTarget.isRemoved()) {
                pendingTarget = null;
            } else if (pendingTarget.hurtTime > 0 && lastPendingTargetHurtTime == 0) {
                combo++;
                lastHitAtMillis = clockMillis.getAsLong();
                pendingTarget = null;
            } else {
                lastPendingTargetHurtTime = pendingTarget.hurtTime;
                if (--pendingSwingTicksLeft <= 0) {
                    pendingTarget = null; // 창 만료 — 빗나간 것으로 취급, 리셋하지 않음
                }
            }
        }

        if (client.player.hurtTime > 0 && lastOwnHurtTime == 0) {
            combo = 0; // 내가 맞으면 즉시 리셋
        }
        lastOwnHurtTime = client.player.hurtTime;

        if (combo > 0 && shouldResetForTimeout(clockMillis.getAsLong(), lastHitAtMillis)) {
            combo = 0; // 3초간 무명중 리셋
        }
    }

    static boolean shouldResetForTimeout(long now, long lastHitAtMillis) {
        return now - lastHitAtMillis >= RESET_AFTER_MILLIS;
    }

    @Override
    public String id() {
        return "combo_counter";
    }

    @Override
    public String displayName() {
        return "Combo Counter";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.36, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = "콤보 " + combo;
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.features.ComboCounterTest"
```
Expected: PASS, 3개 테스트 전부.

- [ ] **Step 5: `CubeClientModClient`에 등록**

`onInitializeClient()`에서 `registry.register(new PingDisplay());` 다음 줄에:

```java
        registry.register(new ComboCounter());
```

import에 `com.cubeclient.mod.features.ComboCounter;` 추가.

- [ ] **Step 6: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/ComboCounter.java mod/src/test/java/com/cubeclient/mod/features/ComboCounterTest.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java
git commit -m "Add ComboCounter: hurtTime-polling hit detection, 3s-timeout or own-damage reset"
```

---

### Task 6: 실기기 수동 검증

**Files:** 없음 — 코드 변경 없는 검증 태스크.

**Interfaces:** 없음.

빌드된 모드를 실제 Minecraft 인스턴스에 붙여 스펙의 "실기기 검증 항목"을 전부 확인한다. Task 1~5는 컴파일·유닛 테스트만 통과했을 뿐 실제 렌더링(아이콘 스케일, 서버 접속, 전투)은 검증되지 않은 상태다.

- [ ] **Step 1: 모드 빌드 및 실행 준비**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat build
```
Expected: BUILD SUCCESSFUL. 사용자에게 `runClient` 태스크 실행 또는 빌드된 모드를 붙인 인스턴스 실행을 안내한다.

- [ ] **Step 2: 리소스팩 표시 확인**

리소스팩을 켜고 끄면서 HUD의 이름 표시가 즉시 반영되는지, 여러 개 켰을 때 쉼표로 나열되는지, 전부 껐을 때 "리소스팩 없음"이 뜨는지 확인.

- [ ] **Step 3: 내구도 표시 확인**

갑옷 4부위 + 손에 든 도구를 장착·해제하며 남은 내구도 숫자가 갱신되는지, 아이템을 쓸수록 숫자가 줄어드는지, HUD 조절 화면에서 배율을 키워도 아이콘과 숫자가 같이 깨지지 않고 커지는지 확인.

- [ ] **Step 4: 핑 표시 확인**

서버(또는 로컬호스트 LAN 월드)에 접속했을 때 핑이 ms 단위로 뜨는지, 싱글플레이 월드에 들어가면 사라지는지(HUD 조절 화면에는 계속 항목으로 잡혀도 실제 플레이 화면엔 안 그려짐) 확인.

- [ ] **Step 5: Combo Counter 확인 — 4가지 전부**

- 몹(PvE)을 연속으로 때려서 콤보 숫자가 올라가는지.
- 다른 플레이어(PvP 가능한 서버)를 때려서도 콤보가 올라가는지.
- 공격 없이 3초 이상 대기하면 콤보가 0으로 리셋되는지.
- 몹이나 다른 플레이어에게 맞으면 3초를 기다리지 않고 즉시 콤보가 0으로 리셋되는지.

- [ ] **Step 6: 문제 발견 시 대응**

실기기에서 스펙과 다르게 동작하는 부분이 있으면(예: 아이콘이 스케일 안에서 안 맞게 그려짐, 감지 창 10틱이 너무 짧거나 길어 명중 인식이 자주 새는 경우) 해당 기능 파일만 수정하고 관련 유닛 테스트(있는 경우)를 다시 통과시킨 뒤 별도 커밋으로 남긴다. 이 계획의 Task 1~5 커밋은 건드리지 않는다.

---

## 자체 검토 결과

- **스펙 커버리지**: 리소스팩(Task 2), 내구도(Task 3), 핑(Task 4), Combo Counter(Task 5), `HudRenderUtil` 리네임(Task 1), 실기기 검증 항목 5가지(Task 6) 전부 스펙과 1:1 대응. 스펙의 "범위 밖" 항목(미니맵, 토글 스니크/스프린트, 서버 리스트, 데미지량/크리티컬, 내구도 경고색)은 어떤 태스크에도 포함되지 않음 — 의도대로.
- **플레이스홀더 스캔**: "TBD"/"나중에"/"적절히 처리" 류 표현 없음. `DurabilityDisplay`(Task 3)에 유닛 테스트가 없는 건 누락이 아니라 스펙이 이미 근거를 명시한 의도적 결정.
- **타입 일관성**: `HudRenderUtil.drawScaled`/`ScaledDrawer`(Task 1에서 확정) 이름을 Task 2~5가 전부 동일하게 사용. 새 기능 4개의 `id()` 문자열(`resource_pack`, `durability`, `ping`, `combo_counter`)은 서로 겹치지 않고 기존 `fps`/`speed`/`cps`/`performance`와도 겹치지 않음 — `FeatureRegistry.register()`가 중복 id에 예외를 던지므로 겹치면 Task 2~5 어딘가에서 즉시 실패해 드러난다.
