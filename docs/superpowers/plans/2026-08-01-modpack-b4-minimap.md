# Sub-project B4: 지형 미니맵 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** M키로 켜고 끄는 원형 미니맵을 추가한다 — 플레이어 중심 실제 블록 색 지형을 북쪽 고정으로 그리고, 그 위에 근처 엔티티(적대/우호/플레이어)를 점으로, 플레이어 자신은 바라보는 방향으로 도는 화살표로 표시한다.

**Architecture:** `TerrainMinimap`(`PositionedHudFeature`, `Category.WORLD`)이 기존 HUD 프레임워크(B1)를 재사용한다. 지형 색 추출(`ChunkColorSampler`)과 틱당 1청크 예산 캐시(`MinimapChunkCache`)가 청크 텍스처 데이터를 관리하고, 그 데이터를 원형으로 잘라 128×128 픽셀 이미지로 합성하는 로직(`MinimapCompositor`)은 Minecraft 객체에 의존하지 않는 순수 함수라 유닛 테스트 가능하다. 원형 처리는 스텐실 버퍼나 오프스크린 프레임버퍼 없이, CPU 쪽에서 `NativeImage`에 픽셀 단위로 알파를 직접 써서 반경 밖을 투명 처리하는 방식으로 충분하다는 걸 이번 계획 작성 중 API 검증으로 확인했다(스펙의 "검증 상태"에 남겨둔 불확실성이 이걸로 해소됨 — 아래 "검증된 API 시그니처" 참고).

**Tech Stack:** Fabric Loom 1.10.2, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader `0.19.3`, Fabric API `0.119.4+1.21.4`(`fabric-key-binding-api-v1` 포함), JDK 21, JUnit 5.

## Global Constraints

- Loom/Yarn/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `gradle.properties`만 참조한다.
- Mixin을 쓰지 않는다 — 이번 계획의 모든 렌더링·데이터 접근이 공개 API로 가능함을 아래 "검증된 API 시그니처"에서 확인했다.
- 토글/설정 변경은 즉시(다음 틱부터) 반영되어야 한다.
- 알 수 없는 설정 id는 무시한다(에러 아님).
- vanilla 키를 폴링할 땐 `wasPressed()`가 아니라 `isPressed()` + 직접 edge 감지를 쓴다(이번 계획엔 vanilla 키 폴링 자체가 없음 — M키는 새 커스텀 키바인딩이고 눌림 판정도 필요 없이 존재만 하면 됨, HUD 표시는 켜짐 설정으로만 제어).
- 매 프레임/매 틱 무거운 작업 금지 — 청크 텍스처 샘플링은 틱당 최대 1개로 예산을 둔다.
- 모드 프로젝트 빌드는 `JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10"`로 `./gradlew.bat`를 실행한다(중첩 디렉터리 정확히 지정).

## 검증된 API 시그니처 (추측 아님 — 실제 jar를 `javap`로 직접 뜯어 확인함, 2026-08-01)

```java
// net.minecraft.world.World
public int getTopY(net.minecraft.world.Heightmap$Type, int, int);
public net.minecraft.registry.RegistryKey<net.minecraft.world.World> getRegistryKey();
public java.util.List<net.minecraft.entity.Entity> getOtherEntities(
    net.minecraft.entity.Entity, net.minecraft.util.math.Box,
    java.util.function.Predicate<? super net.minecraft.entity.Entity>);
public static final net.minecraft.registry.RegistryKey<net.minecraft.world.World> OVERWORLD; // World 클래스의 static 필드
public static final net.minecraft.registry.RegistryKey<net.minecraft.world.World> NETHER;

// net.minecraft.world.Heightmap$Type — enum 상수 중 WORLD_SURFACE 사용(지표면 최상단, 나뭇잎 등 포함)

// net.minecraft.block.AbstractBlock$AbstractBlockState (BlockState가 상속)
public net.minecraft.block.MapColor getMapColor(net.minecraft.world.BlockView, net.minecraft.util.math.BlockPos);

// net.minecraft.block.MapColor
public int getRenderColor(net.minecraft.block.MapColor$Brightness); // ARGB 근사값, 상위 바이트는 신뢰하지 않고 직접 0xFF로 덮어씀
// net.minecraft.block.MapColor$Brightness — enum 상수: LOWEST, LOW, NORMAL, HIGH (이번 계획은 NORMAL만 사용, 높이 음영 효과는 범위 밖)

// net.minecraft.entity.mob.Monster — marker interface, `entity instanceof Monster`로 적대 몹 판정
// net.minecraft.entity.player.PlayerEntity — `entity instanceof PlayerEntity`로 플레이어 판정

// net.minecraft.util.math.ChunkPos
public ChunkPos(int, int);
public boolean equals(Object); // 값 기반, Map/Set 키로 안전
public int hashCode();

// net.minecraft.client.texture.NativeImage
public NativeImage(int, int, boolean); // width, height, useCalloc(초기 픽셀을 0으로)
public int getColorArgb(int, int);
public void setColorArgb(int, int, int);

// net.minecraft.client.texture.NativeImageBackedTexture
public NativeImageBackedTexture(int, int, boolean);
public void upload();
public net.minecraft.client.texture.NativeImage getImage();

// net.minecraft.client.texture.TextureManager (MinecraftClient.getTextureManager()로 얻음)
public void registerTexture(net.minecraft.util.Identifier, net.minecraft.client.texture.AbstractTexture);

// net.minecraft.client.gui.DrawContext
public void drawTexture(java.util.function.Function<Identifier, RenderLayer>, Identifier,
    int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight);

// net.minecraft.client.render.RenderLayer
public static RenderLayer getGuiTextured(Identifier); // DrawContext.drawTexture의 첫 인자로 RenderLayer::getGuiTextured 그대로 사용

// net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper — B3에서 이미 검증됨, 재확인 불필요
```

**이번에 확인해서 스펙의 불확실성을 해소한 것들**:
- **원형 마스크에 스텐실/오프스크린 프레임버퍼가 필요 없다.** 지형 이미지를 어차피 `NativeImage`에 픽셀 단위로 직접 써야 하므로(청크 텍스처 자체가 이 방식), 반경 밖 픽셀은 그냥 알파 0(`0x00000000`)으로 쓰면 된다 — `DrawContext.drawTexture`가 일반 알파 블렌딩으로 그리므로 별도 GL 기법이 전혀 필요 없다.
- **M키는 바닐라 기본 키와 충돌하지 않는다.** 실제 실행 중인 인스턴스(`%APPDATA%\CubeClient\instances\fabric-1.21.4\options.txt`)의 키 목록을 직접 확인했고, `key.keyboard.m`을 쓰는 바닐라 바인딩이 하나도 없다 — B3에서 C키가 `key.saveToolbarActivator`와 겹쳤던 함정이 이번엔 없다.

**Task 1 실제 구현 중 발견된 것 — `ChunkPos`는 순수 값 타입이 아니다.** `net.minecraft.util.math.ChunkPos`의 static 초기화 블록이 `ChunkStatus.FULL`/`ChunkGenerationSteps.GENERATION`(둘 다 실제로 채워진 레지스트리 항목) 을 참조한다(`javap -c`로 static 초기화 바이트코드 확인) — 그래서 게임이 부팅되지 않은 순수 JUnit 환경에서 `new ChunkPos(x, z)`를 호출하면 `IllegalArgumentException: Not bootstrapped`로 죽는다. `int` 한 쌍짜리 값 타입처럼 보여도 순수 테스트에 안전하지 않다. **아래 모든 태스크는 `ChunkPos` 대신 이 모드가 직접 정의하는 순수 값 타입 `ChunkCoord(int x, int z)`를 순수/테스트 계층에서 쓰고, 실제 `World`를 만지는 지점(Task 4의 `ChunkColorSampler`, Task 5의 기본 샘플러 어댑터)에서만 `ChunkPos`로 변환한다.** (참고로 `net.minecraft.world.World`의 `OVERWORLD`/`NETHER` 상수는 `RegistryKey.of(...)`로 키 객체만 만들 뿐 채워진 레지스트리를 참조하지 않아 이런 문제가 없다 — 마찬가지로 `javap -c`로 확인함.)

**확인 안 된 채 남겨두는 것(런타임 시각 확인 필요, Task 7에서 검증)**:
- `getTopY(Heightmap.Type.WORLD_SURFACE, x, z)`가 반환하는 Y가 "그 컬럼의 실제 최상단 블록"인지 "그 한 칸 위(공기)"인지 — 이번 계획은 `-1`을 뺀 값을 실제 블록으로 가정했다(바닐라 지도 아이템과 같은 규약으로 추정). 안 맞으면 Task 4에서 오프셋만 조정하면 되는 국소적 문제다.
- `MapColor.getRenderColor(Brightness)`가 반환하는 정수가 `NativeImage.setColorArgb`가 기대하는 채널 순서와 정확히 맞는지 — 안 맞으면 지형 색이 이상하게(예: 빨강/파랑이 바뀐 것처럼) 보일 것이고, Task 7 실기기 검증에서 바로 눈에 띈다.
- 화살표가 실제로 바라보는 방향을 정확히 가리키는지(yaw 부호 관례는 `Entity.getRotationVector()`가 쓰는 것과 동일한 공식으로 구현했지만, 최종 확인은 눈으로).

---

### Task 1: `ChunkCoord` + `MinimapMath` — 반경·청크 목록 순수 계산

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/ChunkCoord.java`
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/MinimapMath.java`
- Create: `mod/src/test/java/com/cubeclient/mod/minimap/MinimapMathTest.java`

**Interfaces:**
- Consumes: 없음(순수 함수, Minecraft 클래스 의존 전혀 없음 — 아래 `ChunkCoord` 참고).
- Produces: `ChunkCoord(int x, int z)`(record, 순수 값 타입), `MinimapMath.isColumnWithinRadius(double dx, double dz, double radius) -> boolean`, `MinimapMath.chunksInRadius(double playerX, double playerZ, double radius) -> List<ChunkCoord>`. Task 3(엔티티 점 반경 판정)이 `isColumnWithinRadius`를, Task 5(캐시 키)와 Task 6(필요 청크 목록 계산)이 `ChunkCoord`/`chunksInRadius`를 그대로 가져다 쓴다.

**왜 `net.minecraft.util.math.ChunkPos`를 안 쓰는지**: 위 "검증된 API 시그니처" 절 참고 — `ChunkPos`의 static 초기화가 게임 레지스트리를 참조해서 부팅 안 된 JUnit 환경에서 인스턴스화하면 죽는다. `ChunkCoord`는 그 문제가 아예 없는, 이 모드가 직접 정의하는 `int` 두 개짜리 순수 record다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapMathTest {

    @Test
    void centerIsAlwaysWithinRadius() {
        assertTrue(MinimapMath.isColumnWithinRadius(0, 0, 10));
    }

    @Test
    void exactBoundaryIsWithinRadius() {
        assertTrue(MinimapMath.isColumnWithinRadius(10, 0, 10));
    }

    @Test
    void justOutsideBoundaryIsExcluded() {
        assertFalse(MinimapMath.isColumnWithinRadius(10.1, 0, 10));
    }

    @Test
    void diagonalDistanceUsesPythagoras() {
        // sqrt(8^2 + 8^2) = 11.31 > 10
        assertFalse(MinimapMath.isColumnWithinRadius(8, 8, 10));
    }

    @Test
    void chunksInRadiusCoversExpectedBoundingBox() {
        // playerX=0, playerZ=0, radius=20 -> chunk index range floor(-20/16)..floor(20/16) = -2..1 (4칸) 양축
        List<ChunkCoord> chunks = MinimapMath.chunksInRadius(0, 0, 20);

        assertEquals(16, chunks.size());
        assertTrue(chunks.contains(new ChunkCoord(-2, -2)));
        assertTrue(chunks.contains(new ChunkCoord(1, 1)));
        assertFalse(chunks.contains(new ChunkCoord(2, 0)));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.MinimapMathTest"
```
Expected: FAIL — `MinimapMath`/`ChunkCoord` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
// mod/src/main/java/com/cubeclient/mod/minimap/ChunkCoord.java
package com.cubeclient.mod.minimap;

/** 순수 청크 좌표 값 타입. net.minecraft.util.math.ChunkPos는 static 초기화 시 게임
 * 레지스트리를 참조해서 부팅되지 않은 JUnit 환경에서 인스턴스화하면 죽는다(javap로 확인) —
 * 이 모드의 순수/테스트 계층은 전부 이 타입을 쓰고, 실제 게임 안에서 World를 만질 때만
 * ChunkPos로 변환한다. */
public record ChunkCoord(int x, int z) {}
```

```java
// mod/src/main/java/com/cubeclient/mod/minimap/MinimapMath.java
package com.cubeclient.mod.minimap;

import java.util.ArrayList;
import java.util.List;

/** 미니맵 반경 계산에 쓰는 순수 좌표 수학. Minecraft 월드 상태에 의존하지 않는다. */
public final class MinimapMath {
    private MinimapMath() {}

    /** dx, dz는 중심으로부터의 블록 단위 오프셋(어느 축이든 동일 단위면 픽셀 오프셋에도 그대로 쓸 수 있다). */
    public static boolean isColumnWithinRadius(double dx, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** 플레이어 위치를 중심으로 반경을 덮는 데 필요한 청크 좌표 전부(사각 경계 근사, 모서리
     * 청크 몇 개가 실제로는 반경 밖이어도 포함될 수 있다 — MinimapCompositor가 픽셀 단위로
     * 다시 걸러내므로 여기선 손해가 없다). */
    public static List<ChunkCoord> chunksInRadius(double playerX, double playerZ, double radius) {
        int minChunkX = (int) Math.floor((playerX - radius) / 16.0);
        int maxChunkX = (int) Math.floor((playerX + radius) / 16.0);
        int minChunkZ = (int) Math.floor((playerZ - radius) / 16.0);
        int maxChunkZ = (int) Math.floor((playerZ + radius) / 16.0);

        List<ChunkCoord> result = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                result.add(new ChunkCoord(cx, cz));
            }
        }
        return result;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.MinimapMathTest"
```
Expected: PASS, 5개 테스트 전부.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/ChunkCoord.java mod/src/main/java/com/cubeclient/mod/minimap/MinimapMath.java mod/src/test/java/com/cubeclient/mod/minimap/MinimapMathTest.java
git commit -m "Add ChunkCoord and MinimapMath: pure radius/chunk-range calculations for the minimap"
```

---

### Task 2: `EntityBlipClassifier` — 엔티티 점 색상 분류

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/EntityBlipClassifier.java`
- Create: `mod/src/test/java/com/cubeclient/mod/minimap/EntityBlipClassifierTest.java`

**Interfaces:**
- Consumes: 없음(순수 함수). 실제 `Entity` 타입 판정(`instanceof PlayerEntity`/`instanceof Monster`)은 Task 6에서 호출부가 수행하고, 그 결과(boolean 두 개)만 이 함수에 넘긴다 — B2의 `isUserVisiblePack(boolean, ...)`과 같은 패턴(실제 MC 객체 대신 판정 결과만 받는 순수 함수로 분리).
- Produces: `EntityBlipClassifier.BlipColor` enum(`HOSTILE`, `FRIENDLY`, `PLAYER`), `EntityBlipClassifier.classify(boolean isPlayer, boolean isMonster) -> BlipColor`. Task 6이 그대로 가져다 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityBlipClassifierTest {

    @Test
    void playerIsWhite() {
        assertEquals(EntityBlipClassifier.BlipColor.PLAYER, EntityBlipClassifier.classify(true, false));
    }

    @Test
    void monsterIsHostile() {
        assertEquals(EntityBlipClassifier.BlipColor.HOSTILE, EntityBlipClassifier.classify(false, true));
    }

    @Test
    void otherLivingEntityIsFriendly() {
        assertEquals(EntityBlipClassifier.BlipColor.FRIENDLY, EntityBlipClassifier.classify(false, false));
    }

    // 실제로는 플레이어이면서 동시에 Monster인 엔티티는 있을 수 없지만, 우선순위를 명시적으로
    // 고정해둔다(호출부 판정 순서가 바뀌어도 이 함수가 항상 플레이어를 우선하도록).
    @Test
    void playerTakesPriorityOverMonsterFlag() {
        assertEquals(EntityBlipClassifier.BlipColor.PLAYER, EntityBlipClassifier.classify(true, true));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.EntityBlipClassifierTest"
```
Expected: FAIL — `EntityBlipClassifier` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.minimap;

/** 미니맵 위 엔티티 점 색상 분류. 실제 Entity 타입 판정은 호출부(TerrainMinimap)가 하고,
 * 여기엔 그 결과만 넘어온다 — Minecraft 클래스 의존 없이 순수하게 테스트하기 위함. */
public final class EntityBlipClassifier {
    private EntityBlipClassifier() {}

    public enum BlipColor {
        HOSTILE,
        FRIENDLY,
        PLAYER
    }

    public static BlipColor classify(boolean isPlayer, boolean isMonster) {
        if (isPlayer) {
            return BlipColor.PLAYER;
        }
        if (isMonster) {
            return BlipColor.HOSTILE;
        }
        return BlipColor.FRIENDLY;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.EntityBlipClassifierTest"
```
Expected: PASS, 4개 테스트 전부.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/EntityBlipClassifier.java mod/src/test/java/com/cubeclient/mod/minimap/EntityBlipClassifierTest.java
git commit -m "Add EntityBlipClassifier: pure entity-to-dot-color mapping"
```

---

### Task 3: `MinimapCompositor` + `ArrowShape` — 원형 지형 합성과 화살표 도형 (순수)

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/MinimapCompositor.java`
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/ArrowShape.java`
- Create: `mod/src/test/java/com/cubeclient/mod/minimap/MinimapCompositorTest.java`
- Create: `mod/src/test/java/com/cubeclient/mod/minimap/ArrowShapeTest.java`

**Interfaces:**
- Consumes: `MinimapMath.isColumnWithinRadius`(Task 1).
- Produces: `MinimapCompositor.ColumnColorLookup`(함수형 인터페이스, `colorAt(int blockX, int blockZ) -> int`), `MinimapCompositor.composite(int textureSize, double radiusBlocks, double playerX, double playerZ, ColumnColorLookup lookup) -> int[]`(길이 `textureSize*textureSize`, row-major, ARGB, 반경 밖은 0). `ArrowShape.isInsideArrow(double px, double pz, double yawDegrees) -> boolean`(중심 기준 픽셀 오프셋). Task 5(`MinimapChunkCache`가 `ColumnColorLookup` 구현)와 Task 6(둘 다 직접 호출)이 이 시그니처를 그대로 쓴다.

지형 텍스처는 청크 단위가 아니라 **고정 픽셀 해상도**(`textureSize`, Task 6에서 128)로 만든다 — 실제 반경(블록)과 화면에 그려지는 픽셀 수를 분리해서, HUD 편집기의 배율 조절이 "같은 96블록을 더 크게/작게 그리는 것"이 되게 한다(스펙의 결정 그대로). 원형 처리는 반경 밖 픽셀에 그냥 알파 0을 쓰는 것으로 끝난다 — 위 "검증된 API 시그니처"에서 확인했듯 별도 마스크 텍스처나 GL 트릭이 필요 없다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// mod/src/test/java/com/cubeclient/mod/minimap/MinimapCompositorTest.java
package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapCompositorTest {

    @Test
    void pixelsOutsideRadiusAreTransparentAndInsideAreOpaque() {
        // textureSize=4, radiusBlocks=2 -> blocksPerPixel=1. 네 모서리 픽셀만 반경 밖.
        int[] pixels = MinimapCompositor.composite(4, 2.0, 100.0, 200.0, (bx, bz) -> 0xFF112233);

        assertEquals(0, pixels[0]);  // (px=0, py=0) 좌상단 모서리
        assertEquals(0, pixels[3]);  // (px=3, py=0) 우상단 모서리
        assertEquals(0, pixels[12]); // (px=0, py=3) 좌하단 모서리
        assertEquals(0, pixels[15]); // (px=3, py=3) 우하단 모서리
        assertEquals(0xFF112233, pixels[5]);  // (px=1, py=1) 중심 근처
        assertEquals(0xFF112233, pixels[6]);  // (px=2, py=1)

        long opaqueCount = Arrays.stream(pixels).filter(p -> p != 0).count();
        assertEquals(12, opaqueCount);
    }

    @Test
    void lookupReceivesCorrectAbsoluteBlockCoordinates() {
        // textureSize=2, radiusBlocks=2 -> blocksPerPixel=2. 반경이 커서 클리핑 없음(4칸 전부 안).
        // playerX=0, playerZ=0이라 블록 좌표가 그대로 오프셋과 같다.
        int[] pixels = MinimapCompositor.composite(2, 2.0, 0.0, 0.0,
            (bx, bz) -> (bx + 10) * 100 + (bz + 10));

        assertEquals(909, pixels[0]);  // px=0,py=0 -> blockX=-1, blockZ=-1
        assertEquals(1109, pixels[1]); // px=1,py=0 -> blockX=1,  blockZ=-1
        assertEquals(911, pixels[2]);  // px=0,py=1 -> blockX=-1, blockZ=1
        assertEquals(1111, pixels[3]); // px=1,py=1 -> blockX=1,  blockZ=1
    }
}
```

```java
// mod/src/test/java/com/cubeclient/mod/minimap/ArrowShapeTest.java
package com.cubeclient.mod.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrowShapeTest {

    // yaw=0은 바닐라 기준 남쪽(+Z, 화면에서 아래쪽)을 바라본다.
    @Test
    void yawZeroPointsSouthDownScreen() {
        assertTrue(ArrowShape.isInsideArrow(0, 3, 0));
        assertFalse(ArrowShape.isInsideArrow(0, -6, 0));
    }

    // yaw=180은 북쪽(-Z, 화면 위쪽).
    @Test
    void yaw180PointsNorthUpScreen() {
        assertTrue(ArrowShape.isInsideArrow(0, -3, 180));
        assertFalse(ArrowShape.isInsideArrow(0, 6, 180));
    }

    // yaw=90은 서쪽(-X, 화면 왼쪽).
    @Test
    void yaw90PointsWest() {
        assertTrue(ArrowShape.isInsideArrow(-3, 0, 90));
        assertFalse(ArrowShape.isInsideArrow(6, 0, 90));
    }

    @Test
    void farAwayPointIsAlwaysOutside() {
        assertFalse(ArrowShape.isInsideArrow(50, 50, 0));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.MinimapCompositorTest" --tests "com.cubeclient.mod.minimap.ArrowShapeTest"
```
Expected: FAIL — `MinimapCompositor`/`ArrowShape` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
// mod/src/main/java/com/cubeclient/mod/minimap/MinimapCompositor.java
package com.cubeclient.mod.minimap;

/** 캐시된 청크 색상 데이터를 플레이어 중심 원형 이미지로 합성한다. Minecraft 객체에 의존하지
 * 않는다 — 실제 색상 조회는 ColumnColorLookup을 통해 주입받는다(MinimapChunkCache가 구현). */
public final class MinimapCompositor {
    private MinimapCompositor() {}

    @FunctionalInterface
    public interface ColumnColorLookup {
        /** blockX, blockZ 컬럼의 ARGB 색상. 아직 캐시되지 않은 청크는 0(완전 투명)을 반환해야 한다. */
        int colorAt(int blockX, int blockZ);
    }

    /** textureSize x textureSize 픽셀의 ARGB 배열(row-major, index = py*textureSize+px)을 만든다.
     * radiusBlocks는 실제 블록 단위 반경 — textureSize와 별개라서, 같은 반경을 더 크거나 작은
     * 텍스처로 렌더링할 수 있다(HUD 편집기의 배율 조절이 이걸 이용한다). 반경 밖 픽셀은 0. */
    public static int[] composite(int textureSize, double radiusBlocks, double playerX, double playerZ,
                                   ColumnColorLookup lookup) {
        int[] pixels = new int[textureSize * textureSize];
        double half = textureSize / 2.0;
        double blocksPerPixel = radiusBlocks / half;

        for (int py = 0; py < textureSize; py++) {
            double dzBlocks = (py + 0.5 - half) * blocksPerPixel;
            for (int px = 0; px < textureSize; px++) {
                double dxBlocks = (px + 0.5 - half) * blocksPerPixel;
                int index = py * textureSize + px;

                if (!MinimapMath.isColumnWithinRadius(dxBlocks, dzBlocks, radiusBlocks)) {
                    pixels[index] = 0;
                    continue;
                }

                int blockX = (int) Math.floor(playerX + dxBlocks);
                int blockZ = (int) Math.floor(playerZ + dzBlocks);
                pixels[index] = lookup.colorAt(blockX, blockZ);
            }
        }
        return pixels;
    }
}
```

```java
// mod/src/main/java/com/cubeclient/mod/minimap/ArrowShape.java
package com.cubeclient.mod.minimap;

/** 미니맵 중심에 그리는 플레이어 화살표의 순수 도형 판정. yaw 부호 관례는 바닐라
 * Entity.getRotationVector()와 동일: dirX = -sin(yaw), dirZ = cos(yaw) (yaw=0 -> 남쪽/+Z,
 * yaw=180 -> 북쪽/-Z). px, pz는 화살표 중심 기준 픽셀 오프셋. */
public final class ArrowShape {
    private ArrowShape() {}

    private static final double TIP_LENGTH = 6.0;
    private static final double BASE_LENGTH = 5.0;
    private static final double BASE_HALF_WIDTH = 4.0;

    public static boolean isInsideArrow(double px, double pz, double yawDegrees) {
        double yawRad = Math.toRadians(yawDegrees);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double perpX = -dirZ;
        double perpZ = dirX;

        double tipX = dirX * TIP_LENGTH;
        double tipZ = dirZ * TIP_LENGTH;
        double baseCenterX = -dirX * BASE_LENGTH;
        double baseCenterZ = -dirZ * BASE_LENGTH;
        double baseLeftX = baseCenterX + perpX * BASE_HALF_WIDTH;
        double baseLeftZ = baseCenterZ + perpZ * BASE_HALF_WIDTH;
        double baseRightX = baseCenterX - perpX * BASE_HALF_WIDTH;
        double baseRightZ = baseCenterZ - perpZ * BASE_HALF_WIDTH;

        return isInsideTriangle(px, pz, tipX, tipZ, baseLeftX, baseLeftZ, baseRightX, baseRightZ);
    }

    private static boolean isInsideTriangle(double px, double pz, double ax, double az,
                                             double bx, double bz, double cx, double cz) {
        double d1 = cross(px - ax, pz - az, bx - ax, bz - az);
        double d2 = cross(px - bx, pz - bz, cx - bx, cz - bz);
        double d3 = cross(px - cx, pz - cz, ax - cx, az - cz);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static double cross(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.MinimapCompositorTest" --tests "com.cubeclient.mod.minimap.ArrowShapeTest"
```
Expected: PASS, 6개 테스트 전부(Compositor 2개 + Arrow 4개).

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/MinimapCompositor.java mod/src/main/java/com/cubeclient/mod/minimap/ArrowShape.java mod/src/test/java/com/cubeclient/mod/minimap/MinimapCompositorTest.java mod/src/test/java/com/cubeclient/mod/minimap/ArrowShapeTest.java
git commit -m "Add MinimapCompositor and ArrowShape: pure circular terrain compositing and player-arrow geometry"
```

---

### Task 4: `ChunkColorSampler` — 실제 청크 색상 추출 (Minecraft 객체 의존)

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java`

**Interfaces:**
- Consumes: 없음(Task 1~3과 독립).
- Produces: `ChunkColorSampler.sampleChunk(World world, ChunkPos chunkPos) -> int[]`(길이 256, row-major `localZ*16+localX`, ARGB). Task 5(`MinimapChunkCache`)가 기본 샘플러로 사용한다.

`World`/`BlockState`를 직접 다뤄서 유닛 테스트 불가 — B0~B3에서 반복된 것과 같은 이유(실제 게임 월드 없이 인스턴스화 불가). 정확성은 Task 7 실기기 검증에서 눈으로 확인한다(위 "확인 안 된 채 남겨두는 것" 두 항목 참고).

- [ ] **Step 1: 구현 작성**

```java
package com.cubeclient.mod.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** 청크 하나의 16x16 컬럼을 훑어 지표면 최상단 블록의 지도 색을 뽑는다. 동굴(지하) 레이어는
 * 안 본다 — 바닐라 지도 아이템과 동일하게 WORLD_SURFACE 하이트맵만 쓴다. */
public final class ChunkColorSampler {
    private ChunkColorSampler() {}

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                // getTopY는 하이트맵 기준 "그 위 첫 공기 칸"을 돌려주는 것으로 추정 —
                // 실제 블록은 한 칸 아래. Task 7에서 시각적으로 어긋나면 이 -1을 다시 확인한다.
                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
                BlockPos pos = new BlockPos(blockX, topY, blockZ);
                BlockState state = world.getBlockState(pos);
                MapColor mapColor = state.getMapColor(world, pos);
                int rgb = mapColor.getRenderColor(MapColor.Brightness.NORMAL) & 0x00FFFFFF;
                colors[localZ * 16 + localX] = rgb | 0xFF000000;
            }
        }
        return colors;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL(테스트 없음, 컴파일만 확인).

- [ ] **Step 3: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java
git commit -m "Add ChunkColorSampler: extract per-column map colors from a loaded chunk"
```

---

### Task 5: `MinimapChunkCache` — 틱당 1청크 예산 캐시 + 차원 전환 무효화

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/minimap/MinimapChunkCache.java`
- Create: `mod/src/test/java/com/cubeclient/mod/minimap/MinimapChunkCacheTest.java`

**Interfaces:**
- Consumes: `MinimapCompositor.ColumnColorLookup`(Task 3, 이 클래스가 구현), `ChunkCoord`(Task 1). 기본 샘플러는 `ChunkColorSampler::sampleChunk`(Task 4)를 `ChunkCoord -> ChunkPos` 변환 어댑터로 감싸서 쓰지만, 생성자로 다른 샘플러를 주입할 수 있어(ZoomKey의 `LongSupplier` 패턴과 동일) 테스트는 `ChunkCoord`만 다루는 가짜 샘플러로 진행한다 — 테스트 코드가 `net.minecraft.util.math.ChunkPos`를 전혀 안 건드리므로 위에서 발견한 "부팅 안 됨" 문제가 없다.
- Produces: `MinimapChunkCache()`(공개 생성자, 실서비스용), `MinimapChunkCache(BiFunction<World, ChunkCoord, int[]> sampler)`(패키지 전용, 테스트용), `tick(World world, RegistryKey<World> dimension, Set<ChunkCoord> neededChunks)`, `colorAt(int blockX, int blockZ) -> int`(`ColumnColorLookup` 구현). Task 6이 `chunkCache`를 `MinimapCompositor.composite`의 `lookup` 인자로 그대로 넘기고, `MinimapMath.chunksInRadius`가 만든 `Set<ChunkCoord>`를 `tick()`에 그대로 넘긴다.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.cubeclient.mod.minimap;

import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapChunkCacheTest {

    @Test
    void refreshesAtMostOneChunkPerTick() {
        int[] calls = {0};
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> {
            calls[0]++;
            return new int[256];
        });
        Set<ChunkCoord> needed = new LinkedHashSet<>();
        needed.add(new ChunkCoord(0, 0));
        needed.add(new ChunkCoord(1, 0));

        cache.tick(null, World.OVERWORLD, needed);
        assertEquals(1, calls[0]);

        cache.tick(null, World.OVERWORLD, needed);
        assertEquals(2, calls[0]);

        // 이미 둘 다 채워졌으니 세 번째 틱은 다시 샘플링하지 않는다.
        cache.tick(null, World.OVERWORLD, needed);
        assertEquals(2, calls[0]);
    }

    @Test
    void colorAtReturnsSampledValueAfterTick() {
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> {
            int[] colors = new int[256];
            colors[0] = 0xFFAABBCC; // localX=0, localZ=0
            return colors;
        });

        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));

        assertEquals(0xFFAABBCC, cache.colorAt(0, 0));
    }

    @Test
    void colorAtReturnsTransparentForUncachedChunk() {
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> new int[256]);

        assertEquals(0, cache.colorAt(500, 500));
    }

    @Test
    void dimensionChangeClearsCache() {
        MinimapChunkCache cache = new MinimapChunkCache((world, coord) -> {
            int[] colors = new int[256];
            colors[0] = 0xFFAABBCC;
            return colors;
        });
        cache.tick(null, World.OVERWORLD, Set.of(new ChunkCoord(0, 0)));
        assertEquals(0xFFAABBCC, cache.colorAt(0, 0));

        // 차원이 바뀌면 캐시가 비워진다 — 새로 채워지기 전까진 다시 미탐사(투명) 취급.
        cache.tick(null, World.NETHER, Set.of());
        assertEquals(0, cache.colorAt(0, 0));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.MinimapChunkCacheTest"
```
Expected: FAIL — `MinimapChunkCache` 클래스 없음.

- [ ] **Step 3: 최소 구현 작성**

```java
package com.cubeclient.mod.minimap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** 청크별 색상 배열을 캐시하고, 매 tick() 호출마다 최대 1개 청크만 새로 샘플링한다(매 프레임
 * 무거운 작업 금지 원칙). 차원이 바뀌면 캐시 전체를 버린다 — 같은 청크 좌표라도 완전히 다른
 * 지형이기 때문. 캐시 키는 ChunkCoord(순수 값 타입) — 실제 ChunkPos 변환은 기본 샘플러
 * 어댑터 안, 게임이 실제로 도는 순간에만 일어난다. */
public class MinimapChunkCache implements MinimapCompositor.ColumnColorLookup {
    private final BiFunction<World, ChunkCoord, int[]> sampler;
    private final Map<ChunkCoord, int[]> colorsByChunk = new HashMap<>();
    private RegistryKey<World> lastDimension;

    public MinimapChunkCache() {
        this((world, coord) -> ChunkColorSampler.sampleChunk(world, new ChunkPos(coord.x(), coord.z())));
    }

    MinimapChunkCache(BiFunction<World, ChunkCoord, int[]> sampler) {
        this.sampler = sampler;
    }

    public void tick(World world, RegistryKey<World> dimension, Set<ChunkCoord> neededChunks) {
        if (!dimension.equals(lastDimension)) {
            colorsByChunk.clear();
            lastDimension = dimension;
        }

        for (ChunkCoord coord : neededChunks) {
            if (!colorsByChunk.containsKey(coord)) {
                colorsByChunk.put(coord, sampler.apply(world, coord));
                return;
            }
        }
    }

    @Override
    public int colorAt(int blockX, int blockZ) {
        ChunkCoord coord = new ChunkCoord(blockX >> 4, blockZ >> 4);
        int[] chunkColors = colorsByChunk.get(coord);
        if (chunkColors == null) {
            return 0;
        }
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        return chunkColors[localZ * 16 + localX];
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test --tests "com.cubeclient.mod.minimap.MinimapChunkCacheTest"
```
Expected: PASS, 4개 테스트 전부.

- [ ] **Step 5: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/MinimapChunkCache.java mod/src/test/java/com/cubeclient/mod/minimap/MinimapChunkCacheTest.java
git commit -m "Add MinimapChunkCache: tick-budgeted chunk color cache with dimension-change invalidation"
```

---

### Task 6: `TerrainMinimap` — 통합(M키, 렌더링, 엔티티 점, HUD 편집기 크기 보정)

**Files:**
- Create: `mod/src/main/java/com/cubeclient/mod/features/TerrainMinimap.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/registry/PositionedHudFeature.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/gui/HudEditorScreen.java`
- Modify: `mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`
- Modify: `mod/src/main/resources/assets/cubeclient/lang/en_us.json`
- Modify: `mod/src/main/resources/assets/cubeclient/lang/ko_kr.json`

**Interfaces:**
- Consumes: `MinimapMath`(Task 1), `EntityBlipClassifier`(Task 2), `MinimapCompositor`/`ArrowShape`(Task 3), `MinimapChunkCache`(Task 5), `CachedConfig`(B1), `PositionedHudFeature`/`HudRenderUtil`/`HudPosition`(B1), `Category.WORLD`(B0).
- Produces: `TerrainMinimap` 자체(다음 서브프로젝트가 참조할 일 없음). `PositionedHudFeature.renderedWidth()/renderedHeight()` 신규 default 메서드는 앞으로 크기가 다른 HUD 요소가 추가될 때마다 재사용된다.

이 태스크는 통합 작업이라 자체 테스트가 없다 — Task 1~5의 순수 로직은 이미 각자 검증됐고, 이 클래스가 하는 일(월드 조회, 텍스처 업로드, 화면 그리기)은 전부 Minecraft 렌더링 파이프라인에 묶여 있어 Task 7 실기기 검증으로 확인한다.

**먼저, HUD 편집기 크기 근사 문제를 고친다.** 기존 `HudEditorScreen.boundsOf()`는 모든 요소를 80×12 고정 크기로 근사한다(텍스트 한 줄짜리 요소엔 맞았음). 미니맵은 128×128 정사각형이라 이 근사가 완전히 틀어져서 드래그 핸들이 엉뚱한 곳에 뜬다 — B2 최종 리뷰에서 이미 이 문제를 예견해 "선택적 `renderedHeight()` 추가"를 다음 서브프로젝트 후보로 남겨뒀었다(`docs`... 참고할 필요 없이 아래 변경으로 바로 해결).

- [ ] **Step 1: `PositionedHudFeature`에 크기 힌트 default 메서드 추가**

`mod/src/main/java/com/cubeclient/mod/registry/PositionedHudFeature.java` 전체를 아래로 교체:

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

    /** HUD 편집기가 드래그·리사이즈 히트박스를 계산할 때 쓰는 근사 렌더 크기(배율 1.0 기준
     * 픽셀). 텍스트 한 줄짜리 요소는 기본값(80x12)이 실제 렌더 크기와 대체로 맞지만, 미니맵처럼
     * 훨씬 크고 정사각형인 요소는 반드시 재정의해야 편집기 핸들이 실제 렌더링과 어긋나지 않는다. */
    default int renderedWidth() {
        return 80;
    }

    default int renderedHeight() {
        return 12;
    }
}
```

- [ ] **Step 2: `HudEditorScreen.boundsOf()`가 새 메서드를 쓰도록 수정**

`mod/src/main/java/com/cubeclient/mod/gui/HudEditorScreen.java`에서 아래 블록(주석 포함)을 찾아:

```java
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
```

아래로 교체:

```java
    /**
     * 오버레이 사각형의 화면 좌표. 각 기능이 스스로 밝히는 renderedWidth()/renderedHeight()
     * (기본값 80x12, PositionedHudFeature 참고)에 배율을 곱해 근사한다 — 미니맵처럼 크기가
     * 크게 다른 요소는 그 기능이 직접 재정의해서 정확한 히트박스를 제공한다.
     */
    private Bounds boundsOf(Entry entry) {
        int x = (int) (entry.position.xRatio() * width) - OVERLAY_MARGIN;
        int y = (int) (entry.position.yRatio() * height) - OVERLAY_MARGIN;
        int w = (int) (entry.feature.renderedWidth() * entry.position.scale()) + OVERLAY_MARGIN * 2;
        int h = (int) (entry.feature.renderedHeight() * entry.position.scale()) + OVERLAY_MARGIN * 2;
        return new Bounds(x, y, w, h);
    }
```

- [ ] **Step 3: 기존 테스트가 여전히 통과하는지 확인(회귀 없음)**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL — `renderedWidth()`/`renderedHeight()`는 default 메서드라 기존 `PositionedHudFeature` 구현체(SpeedDisplay 등) 전부 그대로 컴파일된다.

- [ ] **Step 4: `TerrainMinimap` 작성**

```java
package com.cubeclient.mod.features;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.minimap.ArrowShape;
import com.cubeclient.mod.minimap.ChunkCoord;
import com.cubeclient.mod.minimap.EntityBlipClassifier;
import com.cubeclient.mod.minimap.MinimapChunkCache;
import com.cubeclient.mod.minimap.MinimapCompositor;
import com.cubeclient.mod.minimap.MinimapMath;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TerrainMinimap implements PositionedHudFeature {
    private static final int TEXTURE_SIZE = 128;
    private static final double RADIUS_BLOCKS = 96.0;
    private static final int ARROW_BOX = 8;
    private static final int ARROW_ARGB = 0xFF2FA968;
    private static final Identifier TEXTURE_ID = Identifier.of("cubeclient", "minimap_composite");

    private final CachedConfig cachedConfig;
    private final MinimapChunkCache chunkCache = new MinimapChunkCache();
    private final KeyBinding minimapKey;
    private NativeImageBackedTexture texture;
    private boolean minimapKeyWasDown;

    public TerrainMinimap(CachedConfig cachedConfig) {
        this.cachedConfig = cachedConfig;
        // M키는 B3의 C키와 달리 실제 실행 중인 인스턴스의 options.txt에서 확인한 결과 바닐라
        // 기본 키와 겹치지 않는다 — InputUtil.isKeyPressed 우회 없이 KeyBinding.isPressed()를
        // 그대로 써도 된다(위 "검증된 API 시그니처" 참고).
        this.minimapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cubeclient.minimap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.cubeclient"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    // 렌더 루프(중앙 디스패치)와 별개로 이 리스너는 스스로 등록한 것이라, 청크 캐시 예산 소비는
    // 켜짐 여부를 직접 확인해야 한다(ToggleSprint/ZoomKey와 같은 이유 — B3 아키텍처 참고). M키
    // 토글 자체는 켜짐 여부와 무관하게 항상 눌림을 감지해야 하므로 그 확인보다 먼저 처리한다.
    private void onTick(MinecraftClient client) {
        boolean isDown = minimapKey.isPressed();
        if (isDown && !minimapKeyWasDown) {
            toggleEnabled();
        }
        minimapKeyWasDown = isDown;

        if (client.player == null || client.world == null || !cachedConfig.current().isEnabled(id())) {
            return;
        }
        Set<ChunkCoord> needed = new HashSet<>(
            MinimapMath.chunksInRadius(client.player.getX(), client.player.getZ(), RADIUS_BLOCKS));
        chunkCache.tick(client.world, client.world.getRegistryKey(), needed);
    }

    // 모드 목록 화면의 체크박스(ModListScreen.onToggle)와 정확히 같은 read-modify-write
    // 패턴 — M키는 그 체크박스의 단축키일 뿐, 별도 상태를 두지 않는다.
    private void toggleEnabled() {
        ModConfig current = cachedConfig.current();
        Map<String, Boolean> enabled = new HashMap<>(current.enabled());
        enabled.put(id(), !current.isEnabled(id()));
        try {
            cachedConfig.save(new ModConfig(enabled, current.favorites(), current.positions()));
        } catch (IOException e) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("설정을 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        if (texture == null) {
            texture = new NativeImageBackedTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
        }

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        int[] pixels = MinimapCompositor.composite(TEXTURE_SIZE, RADIUS_BLOCKS, playerX, playerZ, chunkCache);
        stampArrow(pixels, client.player.getYaw());

        NativeImage image = texture.getImage();
        for (int py = 0; py < TEXTURE_SIZE; py++) {
            for (int px = 0; px < TEXTURE_SIZE; px++) {
                image.setColorArgb(px, py, pixels[py * TEXTURE_SIZE + px]);
            }
        }
        texture.upload();

        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) -> {
            ctx.drawTexture(RenderLayer::getGuiTextured, TEXTURE_ID,
                x, y, 0f, 0f, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            drawEntityDots(ctx, x, y, client, playerX, playerZ);
        });
    }

    private void stampArrow(int[] pixels, float yawDegrees) {
        int half = TEXTURE_SIZE / 2;
        for (int dz = -ARROW_BOX; dz <= ARROW_BOX; dz++) {
            for (int dx = -ARROW_BOX; dx <= ARROW_BOX; dx++) {
                if (!ArrowShape.isInsideArrow(dx, dz, yawDegrees)) {
                    continue;
                }
                int px = half + dx;
                int pz = half + dz;
                if (px >= 0 && px < TEXTURE_SIZE && pz >= 0 && pz < TEXTURE_SIZE) {
                    pixels[pz * TEXTURE_SIZE + px] = ARROW_ARGB;
                }
            }
        }
    }

    private void drawEntityDots(DrawContext ctx, int x, int y, MinecraftClient client,
                                 double playerX, double playerZ) {
        double playerY = client.player.getY();
        Box searchBox = new Box(
            playerX - RADIUS_BLOCKS, playerY - 64, playerZ - RADIUS_BLOCKS,
            playerX + RADIUS_BLOCKS, playerY + 64, playerZ + RADIUS_BLOCKS);
        List<Entity> nearby = client.world.getOtherEntities(client.player, searchBox,
            entity -> entity instanceof LivingEntity);

        double half = TEXTURE_SIZE / 2.0;
        double blocksPerPixel = RADIUS_BLOCKS / half;
        for (Entity entity : nearby) {
            double dx = entity.getX() - playerX;
            double dz = entity.getZ() - playerZ;
            if (!MinimapMath.isColumnWithinRadius(dx, dz, RADIUS_BLOCKS)) {
                continue;
            }
            EntityBlipClassifier.BlipColor blip = EntityBlipClassifier.classify(
                entity instanceof PlayerEntity, entity instanceof Monster);
            int color = blipArgb(blip);
            int px = x + (int) (dx / blocksPerPixel + half);
            int pz = y + (int) (dz / blocksPerPixel + half);
            ctx.fill(px - 1, pz - 1, px + 1, pz + 1, color);
        }
    }

    private static int blipArgb(EntityBlipClassifier.BlipColor blip) {
        return switch (blip) {
            case HOSTILE -> 0xFFE05A5A;
            case FRIENDLY -> 0xFF6FCF7A;
            case PLAYER -> 0xFFF2F2F2;
        };
    }

    @Override
    public String id() {
        return "minimap";
    }

    @Override
    public String displayName() {
        return "미니맵";
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.72, 0.03, 0.5);
    }

    @Override
    public int renderedWidth() {
        return TEXTURE_SIZE;
    }

    @Override
    public int renderedHeight() {
        return TEXTURE_SIZE;
    }
}
```

- [ ] **Step 5: `CubeClientModClient`에 등록**

`mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java`에 import 추가:

```java
import com.cubeclient.mod.features.TerrainMinimap;
```

`registry.register(new ZoomKey(cachedConfig));` 다음 줄에 추가:

```java
        registry.register(new TerrainMinimap(cachedConfig));
```

- [ ] **Step 6: 키바인딩 표시 이름을 언어 파일에 추가**

`mod/src/main/resources/assets/cubeclient/lang/en_us.json`을 아래로 교체:

```json
{
  "key.categories.cubeclient": "CubeClient",
  "key.cubeclient.minimap": "Minimap",
  "key.cubeclient.zoom": "Zoom"
}
```

`mod/src/main/resources/assets/cubeclient/lang/ko_kr.json`을 아래로 교체:

```json
{
  "key.categories.cubeclient": "CubeClient",
  "key.cubeclient.minimap": "미니맵",
  "key.cubeclient.zoom": "확대(Zoom)"
}
```

- [ ] **Step 7: 전체 컴파일 및 테스트 통과 확인**

Run:
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat test
```
Expected: BUILD SUCCESSFUL, 지금까지 만든 유닛 테스트 전부 포함해서 통과.

- [ ] **Step 8: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/features/TerrainMinimap.java mod/src/main/java/com/cubeclient/mod/registry/PositionedHudFeature.java mod/src/main/java/com/cubeclient/mod/gui/HudEditorScreen.java mod/src/main/java/com/cubeclient/mod/CubeClientModClient.java mod/src/main/resources/assets/cubeclient/lang/en_us.json mod/src/main/resources/assets/cubeclient/lang/ko_kr.json
git commit -m "Add TerrainMinimap: M-key circular terrain minimap with entity blips and player arrow"
```

---

### Task 7: 실기기 수동 검증

**Files:** 없음(코드 변경 없음, 실제 게임 실행으로 확인만).

**Interfaces:**
- Consumes: Task 1~6 전체.
- Produces: 없음 — 검증 결과에 따라 이전 태스크로 돌아가 수정할 수 있다(특히 Task 4의 `-1` 오프셋, 색상 채널 순서, 화살표 방향).

빌드한 jar를 실제 CubeClient 런처 인스턴스의 `mods/` 폴더(`%APPDATA%\CubeClient\instances\fabric-1.21.4\mods\`와 `fabric-1.21\mods\` 양쪽 다 — B3에서 확인했듯 어느 게 실제 쓰이는지 확실치 않으니 둘 다 최신으로 유지)에 넣고 실제 런처로 게임을 켠다.

- [ ] **Step 1: 빌드 및 배포**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat build
```
빌드된 jar(`mod/build/libs/` 아래, `-sources`/`-dev` 아닌 것)를 두 인스턴스의 `mods/` 폴더에 복사.

- [ ] **Step 2: 실행 및 확인**

큐브클라이언트 런처로 게임 실행(바탕화면 바로가기 또는 `launch-cubeclient.bat`) → 모드 목록 화면에서 미니맵 켜기 → 월드 접속.

확인 항목:
- M키를 누를 때마다 미니맵이 켜지고 꺼지는지(모드 목록 화면의 체크박스도 같은 상태를 반영하는지 — 둘 다 같은 `enabled` 값을 보고 쓰므로 서로 동기화돼야 한다).
- 지형 색이 실제 주변 지형과 대략 맞아 보이는지(정확한 색조보다 "풀은 초록, 물은 파랑, 돌은 회색" 수준으로 확인).
- 원형으로 잘려 보이는지(네 모서리가 사각형으로 삐져나오지 않는지).
- 이동하면서 새 청크가 끊김 없이 채워지는지, FPS 저하가 없는지.
- 화살표가 실제로 바라보는 방향과 일치하는지(플레이어를 좌우로 돌려보며 확인).
- 엔티티 점 색상이 맞는지(좀비=빨강, 동물=초록, 다른 플레이어=흰색 — 멀티에서 확인 어려우면 싱글로 좀비/동물만이라도).
- 차원 이동(네더 포털) 후 미니맵이 새 지형으로 갱신되는지, 예전 지형이 안 남아있는지.
- HUD 편집기에서 미니맵을 드래그·리사이즈했을 때 핸들이 실제 렌더링 크기와 맞는지(Step 1~2에서 고친 부분).
- "위치 초기화" 버튼이 미니맵도 기본 위치로 되돌리는지.

- [ ] **Step 3: 발견된 문제 수정 및 재검증**

문제가 있으면 해당 태스크 파일을 직접 고치고(예: Task 4의 `-1` 오프셋 제거/변경, Task 6의 M키 토글 로직 추가), 관련 유닛 테스트가 있으면 다시 돌리고, 없으면(대부분 이 태스크의 문제는 MC 객체 의존이라 유닛 테스트 불가) 다시 빌드해서 재배포·재확인한다.

- [ ] **Step 4: 최종 커밋**

```bash
git add -A
git commit -m "Fix issues found during B4 minimap real-device verification"
```
(문제가 없었다면 이 스텝은 생략 — 커밋할 변경사항이 없다.)

---

### Task 8: `ChunkColorSampler`에 높이 음영(relief shading) 추가 — 실기기 검증 후 추가된 태스크

**배경**: Task 7 실기기 검증에서 기능은 전부 정상 동작했지만("잘 된다"), 사용자가 "지도가 표현할 수 있는 색깔을 늘리자, 너무 밍밍해"라고 피드백했다. 원인은 `ChunkColorSampler`가 모든 칸을 `MapColor.Brightness.NORMAL` 고정으로만 그려서다 — 바닐라 지도 아이템이 실제로 다채로워 보이는 건 블록 색 종류가 많아서가 아니라, 인접한 칸끼리 높이 차이에 따라 밝기(LOW/NORMAL/HIGH)를 다르게 줘서 생기는 굴곡 음영 효과다. 이 계획의 "검증된 API 시그니처" 절에 `MapColor.Brightness` enum 상수(`LOWEST`, `LOW`, `NORMAL`, `HIGH`)가 이미 `javap`로 확인돼 있다.

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java`

**Interfaces:**
- Consumes: 없음(기존 시그니처 그대로).
- Produces: `sampleChunk(World, ChunkPos) -> int[]` 시그니처는 변경 없음 — 내부 색상 계산 로직만 바뀐다. 다른 태스크에 영향 없음(테스트 없음, MC 객체 의존).

- [ ] **Step 1: 구현 수정**

`mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java`를 아래로 교체:

```java
package com.cubeclient.mod.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** 청크 하나의 16x16 컬럼을 훑어 지표면 최상단 블록의 지도 색을 뽑는다. 동굴(지하) 레이어는
 * 안 본다 — 바닐라 지도 아이템과 동일하게 WORLD_SURFACE 하이트맵만 쓴다. 인접한 칸(북쪽,
 * blockZ-1)과의 높이 차이로 밝기(LOW/NORMAL/HIGH)를 다르게 줘서 굴곡 음영을 낸다 — 바닐라
 * 지도가 실제로 다채로워 보이는 이유가 이 음영이지 블록 색 종류가 아니다(실기기 검증에서
 * "너무 밍밍하다"는 피드백을 받고 추가됨). */
public final class ChunkColorSampler {
    private ChunkColorSampler() {}

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                // getTopY는 하이트맵 기준 "그 위 첫 공기 칸"을 돌려주는 것으로 추정 —
                // 실제 블록은 한 칸 아래.
                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
                int northTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ - 1) - 1;

                BlockPos pos = new BlockPos(blockX, topY, blockZ);
                BlockState state = world.getBlockState(pos);
                MapColor mapColor = state.getMapColor(world, pos);

                MapColor.Brightness brightness = topY > northTopY ? MapColor.Brightness.HIGH
                    : topY < northTopY ? MapColor.Brightness.LOW
                    : MapColor.Brightness.NORMAL;

                int rgb = mapColor.getRenderColor(brightness) & 0x00FFFFFF;
                colors[localZ * 16 + localX] = rgb | 0xFF000000;
            }
        }
        return colors;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL(이 파일엔 원래도 테스트 없음, Task 4와 동일한 이유).

- [ ] **Step 3: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java
git commit -m "Add height-based brightness shading to ChunkColorSampler for visual richness"
```

- [ ] **Step 4: 재빌드·재배포 후 실기기 재확인**

Task 7과 같은 방식으로 빌드해서 두 인스턴스 `mods/`에 배포하고, 지형이 이전보다 입체감 있게(굴곡이 도드라지게) 보이는지 확인한다.

---

### Task 9: `ChunkColorSampler`의 검은 구멍 버그 수정 — 실기기 검증 후 추가된 태스크

**배경**: Task 8 이후 사용자가 스크린샷으로 미니맵 일부가 "아무리 왔다갔다 해도 계속 검은색"인 지점을 보고했다. 원인을 `javap`로 확인: `MapColor.CLEAR`(장식용 블록 — 꽃, 잔디, 눈 쌓임 등에 쓰이는 "색 없음" 지도색)의 원본 색상값 자체가 `new MapColor(0, 0)` — 즉 raw color가 **0(검정)**이다. `Heightmap.Type.WORLD_SURFACE`가 잡는 "맨 위 블록"은 공기만 아니면 되므로, 잔디밭·꽃밭·눈 쌓인 땅처럼 장식 블록이 진짜 지형 위에 얹혀 있는 곳에서는 그 장식 블록이 "맨 위"로 잡히고, 그 블록의 `MapColor.CLEAR`를 그대로 그려서 **투명(데이터 없음)이 아니라 불투명 검정**이 칠해진다 — `& 0x00FFFFFF | 0xFF000000`가 CLEAR의 raw color 0에 강제로 알파 0xFF를 씌우기 때문. 그 지점은 실제로 늘 같은 장식 블록이 덮여 있으니, 아무리 움직여도 계속 검게 남는다(청크가 아직 캐시 안 된 경우의 "일시적 투명"과는 다른, 영구적인 버그).

**Files:**
- Modify: `mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java`

**Interfaces:**
- Consumes: 없음(기존 시그니처 그대로).
- Produces: `sampleChunk(World, ChunkPos) -> int[]` 시그니처 변경 없음 — 내부 최상단 블록 탐색 로직만 바뀐다.

**검증된 API**: `net.minecraft.world.HeightLimitView.getBottomY() -> int`(`World`가 상속, `javap`로 확인) — 아래로 내려가는 탐색의 바닥 한계로 쓴다. `MapColor.CLEAR`는 `net.minecraft.block.MapColor`의 `public static final` 필드(이미 이 계획에서 검증됨).

- [ ] **Step 1: 구현 수정**

`mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java`를 아래로 교체:

```java
package com.cubeclient.mod.minimap;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** 청크 하나의 16x16 컬럼을 훑어 지표면 최상단 블록의 지도 색을 뽑는다. 동굴(지하) 레이어는
 * 안 본다 — 바닐라 지도 아이템과 동일하게 WORLD_SURFACE 하이트맵만 쓴다. 인접한 칸(북쪽,
 * blockZ-1)과의 높이 차이로 밝기(LOW/NORMAL/HIGH)를 다르게 줘서 굴곡 음영을 낸다. */
public final class ChunkColorSampler {
    private ChunkColorSampler() {}

    // WORLD_SURFACE가 잡는 "맨 위 블록"은 꽃·잔디·눈 쌓임 같은 장식 블록도 포함하는데, 이런
    // 블록의 지도색은 MapColor.CLEAR(원본 색상값 0=검정)라 그대로 쓰면 검은 구멍이 생긴다.
    // 색 있는 블록이 나올 때까지 최대 이만큼만 아래로 내려간다(무한정 파면 청크 하나 처리
    // 비용이 너무 커진다 — 실기기에서 검은 구멍 버그로 발견됨).
    private static final int MAX_CLEAR_SKIP = 24;

    public static int[] sampleChunk(World world, ChunkPos chunkPos) {
        int[] colors = new int[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int blockX = chunkPos.getStartX() + localX;
                int blockZ = chunkPos.getStartZ() + localZ;
                int topY = findColoredTopY(world, blockX, blockZ);
                int northTopY = findColoredTopY(world, blockX, blockZ - 1);

                BlockPos pos = new BlockPos(blockX, topY, blockZ);
                BlockState state = world.getBlockState(pos);
                MapColor mapColor = state.getMapColor(world, pos);

                MapColor.Brightness brightness = topY > northTopY ? MapColor.Brightness.HIGH
                    : topY < northTopY ? MapColor.Brightness.LOW
                    : MapColor.Brightness.NORMAL;

                int rgb = mapColor.getRenderColor(brightness) & 0x00FFFFFF;
                colors[localZ * 16 + localX] = rgb | 0xFF000000;
            }
        }
        return colors;
    }

    /** 색 있는(CLEAR가 아닌) 블록이 나올 때까지 아래로 내려간다. */
    private static int findColoredTopY(World world, int blockX, int blockZ) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ) - 1;
        int floor = Math.max(world.getBottomY(), y - MAX_CLEAR_SKIP);
        while (y > floor) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            MapColor mapColor = world.getBlockState(pos).getMapColor(world, pos);
            if (mapColor != MapColor.CLEAR) {
                return y;
            }
            y--;
        }
        return y;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run (`mod/` 디렉터리에서):
```bash
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add mod/src/main/java/com/cubeclient/mod/minimap/ChunkColorSampler.java
git commit -m "Fix ChunkColorSampler: skip MapColor.CLEAR blocks (decorative plants/snow) that were rendering as opaque black"
```

- [ ] **Step 4: 재빌드·재배포 후 실기기 재확인**

Task 7과 같은 방식으로 빌드해서 두 인스턴스 `mods/`에 배포하고, 이전에 검게 보이던 지점(꽃밭·잔디·눈밭 등)이 이제 정상 색으로 보이는지 확인한다.
