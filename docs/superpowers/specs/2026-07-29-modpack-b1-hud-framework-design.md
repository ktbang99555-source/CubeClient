# Sub-project B1: HUD 프레임워크 + 위치/크기 편집기 + 속도·CPS·성능 — 설계

## 목표

B0은 모드 골격(Feature/FeatureRegistry/ModConfig/ModListScreen)과 기능 1개(FPS 표시)만 갖고 있었다. B1은 두 가지를 더한다:

1. HUD에 그려지는 기능이 화면 위 위치와 크기를 가질 수 있는 프레임워크, 그리고 그걸 마우스로 직접 조절하는 전용 화면.
2. 그 프레임워크를 실제로 쓰는 기능 3개: 속도(m/s), CPS, 성능(CPU+RAM).

부수적으로 B0에서 발견됐지만 미루린 두 가지도 여기서 고친다: 모드 목록 화면 스크롤, deprecated `HudRenderCallback`에서 벗어나기.

## 범위 밖 (B1에서 안 하는 것)

- GPU 사용률 — 표준 JVM API로는 못 읽는다. nvidia-smi 같은 외부 프로세스나 네이티브 라이브러리가 필요한데, 이 프로젝트가 이미 두 번 데인 "네이티브/버전 지뢰" 패턴과 같은 리스크라 이번엔 제외. 필요해지면 별도 서브프로젝트로.
- 미니맵, 죽은 위치 표시, 서버 리스트·핑, 갑옷·도구 내구도, Combo Counter, Toggle Sneak/Sprint, C키 Zoom — 전부 로드맵상 B2~B5.
- 꺼진 기능을 HUD 조절 화면에 표시하는 것 — 켜진 것만 조절 대상.
- HUD 조절 화면이 열려 있는 동안 게임을 계속 진행시키는 것 — 싱글플레이는 기존 Screen 방식 그대로 자연히 일시정지된다(일시정지 메뉴와 동일한 동작이며, 이 프로젝트가 새로 만드는 예외가 아니다). 멀티플레이/서버는 마인크래프트 자체가 메뉴를 열어도 월드를 멈추지 않으므로 그대로 진행된다 — 이 역시 손대지 않는다.

## 아키텍처

### `PositionedHudFeature` — 새 서브인터페이스

```java
public interface PositionedHudFeature extends Feature {
    HudPosition defaultPosition();
    void render(DrawContext context, HudPosition resolvedPosition);
}
```

`Feature` 자체에 이 메서드들을 넣지 않는다. 조작·월드·서버 카테고리의 기능(예: B3의 Toggle Sneak/Sprint)은 화면에 좌표를 갖는 요소가 아니므로 이 인터페이스를 구현하지 않는다. `FeatureRegistry`는 지금처럼 `Feature`만 다루며 변경 없음 — HUD 조절 화면과 HUD 레이어 등록 코드만 `registry.all()`을 순회하며 `instanceof PositionedHudFeature`로 골라낸다.

### `HudPosition` — 새 record

```java
public record HudPosition(double xRatio, double yRatio, double scale) {
    // 컴팩트 생성자에서 클램프한다 — Gson은 static of()를 거치지 않고 이 캐노니컬 생성자를
    // 리플렉션으로 직접 호출하므로(B0의 ModConfig에서 확인된 동작), 손상된 설정 파일이 만든
    // 범위 밖 값도 여기서 걸러야 of()를 거친 값과 똑같이 안전해진다.
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

- `xRatio`/`yRatio`: 요소의 왼쪽-위 모서리가 화면 너비/높이에서 차지하는 비율(0.0~1.0). 창 크기나 GUI 배율이 바뀌어도 상대 위치가 유지된다.
- `scale`: 기본 크기 대비 배율. 0.5~3.0으로 클램프 — 0 이하나 지나치게 큰 값으로 저장되어 렌더링이 깨지는 걸 막는다(설정 파일은 사용자가 손으로 편집할 수 있는 파일이므로).
- 렌더링 시점 실제 픽셀 좌표 계산은 `xRatio * screenWidth`, `yRatio * screenHeight` — 이 계산은 `HudPosition`이 아니라 호출부(레이어 렌더러)에 둔다. `HudPosition`은 순수 데이터로 유지해 Gson 직렬화가 단순하게 남는다(B0에서 `ModConfig`에 적용한 원칙과 동일).

### `ModConfig` 확장

```java
public record ModConfig(
    Map<String, Boolean> enabled,
    Set<String> favorites,
    Map<String, HudPosition> positions
) {
    public ModConfig {
        enabled = enabled == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(enabled));
        favorites = favorites == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(favorites));
        positions = positions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(positions));
    }

    public static ModConfig empty() {
        return new ModConfig(Map.of(), Set.of(), Map.of());
    }

    public boolean isEnabled(String featureId) { /* 기존과 동일 */ }

    public HudPosition positionOr(String featureId, HudPosition fallback) {
        return positions.getOrDefault(featureId, fallback);
    }
}
```

B0의 `ModConfig` compact 생성자가 잡아낸 것과 같은 문제(Gson이 없는 필드를 null로 채움)가 새 `positions` 필드에도 그대로 적용되므로 처음부터 같은 방식으로 null을 정규화한다. `HudPosition` 자체도 record라 Gson이 필드 일부가 빠진 JSON을 만나면 `null` 컴포넌트를 만들 수 있다 — `positionOr`가 그 경우 조회 결과가 아예 없는 것으로 취급하도록 `Map.getOrDefault`만 쓰고, `HudPosition` 내부에 null 필드가 있는 상태로 반환되는 경우는 만들지 않는다(저장은 항상 `HudPosition.of(...)`를 거치므로 필드가 부분적으로만 채워진 채 디스크에 쓰일 일이 없다).

기존 설정 파일(`positions` 필드가 아예 없는 B0 시절 파일)을 읽으면 `positions`가 빈 맵이 되고, 모든 기능이 `defaultPosition()`을 쓴다 — 마이그레이션 코드 불필요.

### HUD 렌더링: `HudRenderCallback`에서 `HudLayerRegistrationCallback`으로

`HudRenderCallback`은 사용 중인 Fabric API 버전(0.119.4+1.21.4, 번들된 `fabric-rendering-v1-10.2.1+0d31b09f04.jar`)에서 `@Deprecated`로 확인됨(클래스 파일의 `RuntimeVisibleAnnotations`에 `java.lang.Deprecated` 존재, 실제로 jar를 풀어서 `javap -v`로 검증함 — 추측 아님). 대체 API도 같은 jar 안에 있다:

```java
public interface HudLayerRegistrationCallback {
    Event<HudLayerRegistrationCallback> EVENT;
    void register(LayeredDrawerWrapper drawer);
}
```

`LayeredDrawerWrapper.attachLayerAfter(Identifier anchor, IdentifiedLayer layer)`로 원하는 바닐라 레이어 뒤에 우리 레이어를 붙인다. B1에서는 `IdentifiedLayer.MISC_OVERLAYS` 뒤에 CubeClient 레이어 하나를 붙이고, 그 레이어 안에서 켜진 `PositionedHudFeature`를 전부 순회해 그린다(레이어를 기능 개수만큼 등록하지 않는다 — 하나로 충분하고, 등록/해제를 기능 토글마다 다시 할 필요가 없다).

B0의 `FpsDisplay`도 이번에 `PositionedHudFeature`로 마이그레이션한다(그대로 두면 새 레이어 시스템과 옛 콜백이 공존하게 되어 렌더 순서가 예측 불가능해짐).

```java
public class FpsDisplay implements PositionedHudFeature {
    // id(), displayName(), category()는 기존과 동일
    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.01, 1.0); // 기존 (4,4) 근처, 화면 좌상단
    }
    @Override
    public void render(DrawContext context, HudPosition pos) {
        // 기존 render(DrawContext) 본문을 pos 기준 픽셀 좌표로 옮김
    }
}
```

설정 캐시 문제(B0에서 "매 프레임 disk 읽기, 성능보다 정확성 우선"으로 의도적으로 남겨둔 것)는 이번에 고친다: 레이어 콜백이 등록될 때 `ConfigStore`를 감싸는 작은 인메모리 캐시를 두고, `ModListScreen`과 `HudEditorScreen`이 `save()`를 호출할 때마다 그 캐시도 같이 갱신한다. 여러 HUD 기능이 프레임마다 각자 disk I/O를 하는 걸 막는 게 목적이며, 캐시와 disk가 어긋나는 경우는 이 모드 프로세스 안에서 설정을 쓰는 경로가 `ConfigStore.save()` 하나뿐이므로 발생하지 않는다.

### `HudEditorScreen` — 새 화면

```java
public class HudEditorScreen extends Screen {
    // 생성자: (Screen parent, FeatureRegistry registry, ConfigStore configStore)
}
```

- `init()`에서 켜진 `PositionedHudFeature` 각각에 대해 드래그 가능한 오버레이 사각형을 만든다. 오버레이는 실제 HUD 렌더링과 별개로 그려지는 "핸들"이며(즉 `FpsDisplay.render()` 그대로 재사용하면서 그 위에 테두리+리사이즈 핸들만 겹쳐 그림), 기능 자체의 `render()` 로직을 두 번 구현하지 않는다.
- 각 오버레이는 본체 드래그(이동, `xRatio`/`yRatio` 갱신)와 우하단 8x8 핸들 드래그(리사이즈, `scale` 갱신)를 `mouseDragged`에서 히트테스트로 구분한다 — B0의 `FeatureCard.onClick()`이 하트/톱니/토글 세 영역을 히트테스트로 나눈 것과 같은 패턴.
- 드래그 종료(`mouseReleased`) 시점에 `ConfigStore.save()` — 매 프레임 저장하지 않는다(드래그 중 매 픽셀마다 disk I/O는 불필요한 부하).
- 하단에 "나가기" 버튼 하나, 누르면 `client.setScreen(parent)`로 `ModListScreen`으로 복귀 — B0의 `ModListScreen.close()`가 쓰는 것과 같은 parent-복귀 패턴.
- 배경은 실제 게임 월드가 보이도록 렌더링(불투명 패널로 덮지 않음) — HUD가 실제 게임 화면 위 어디에 앉는지 보면서 조절해야 하므로.

### `ModListScreen` 변경 — "HUD 조절" 탭

`Category` enum에 새 항목을 추가하지 않는다. `Category`는 `FeatureRegistry.list()`의 정렬·필터 기준이자 카드 아이콘 종류로 이미 쓰이고 있고, "HUD 조절"은 필터가 아니라 별도 화면으로의 이동이라 의미가 다르다. 대신 `ModListScreen.init()`의 탭 생성 루프 마지막에 버튼 하나를 더 추가한다:

```java
addDrawableChild(ButtonWidget.builder(Text.literal("HUD 조절"), b -> {
    client.setScreen(new HudEditorScreen(this, registry, configStore));
}).dimensions(tabX, tabY, tabWidth, TAB_HEIGHT).build());
```

탭 폭 계산(`tabCount`)에 이 탭도 포함시켜야 폭 320 제약(B0에서 실기기로 확인한 제약) 안에서 계속 맞아떨어진다.

### 모드 목록 스크롤

`ModListScreen`에 `int scrollOffset` 필드 추가.

- `mouseScrolled(mouseX, mouseY, amountX, amountY)` 오버라이드 — `scrollOffset -= amountY * SCROLL_STEP`, `0`과 `maxScroll`(카드 총 행 수 기준 계산) 사이로 클램프.
- `rebuildCards()`의 각 카드 y좌표에서 `scrollOffset`을 뺀다.
- 그리드 영역(`GRID_TOP`부터 화면 하단 여백까지) 밖으로 나간 카드는 `context.enableScissor(...)`로 잘라내고 그 밖에서는 `render()`가 그 카드를 건너뛴다 — 화면 밖 카드가 계속 클릭 판정을 받는 걸 막는 목적도 있음(스크롤로 안 보이는 카드가 우연히 다른 위치의 보이는 카드와 겹쳐 클릭되는 사고 방지).

### B1이 추가하는 기능 3개

모두 `mod/src/main/java/com/cubeclient/mod/features/`에 위치, 전부 `Category.HUD`, 전부 `PositionedHudFeature` 구현.

**`SpeedDisplay`** — 수평 이동속도(m/s). 매 틱 `ClientTickEvents.END_CLIENT_TICK`에서 플레이어 XZ 좌표 델타를 재서 저장해두고, `render()`는 그 값을 읽기만 한다(렌더 스레드에서 좌표를 다시 계산하지 않음 — 렌더는 틱보다 자주 호출될 수 있어 델타가 0에 가까워져 값이 튐).

**`CpsDisplay`** — 좌클릭 CPS. `KeyBinding.wasPressed()`는 호출될 때마다 내부 카운터를 1씩 소모하며 그 프레임에 눌림이 있었는지를 반환하는 방식이라, 틱마다 한 번만 호출하면 한 틱에 여러 번 눌린 클릭 중 한 번만 셀 수 있다(20틱/초보다 빠른 연타를 놓침). 그래서 `while (client.options.attackKey.wasPressed())` 형태로 큐를 완전히 비우면서 각 소모마다 타임스탬프를 리스트에 쌓고, 렌더 시점에 "지금부터 1초 이내" 타임스탬프 개수를 센다(1초 롤링 윈도우).

**`PerformanceDisplay`** — CPU+RAM. `((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad()`(0.0~1.0, 프로세스 기준 — 시스템 전체가 아니라 이 게임 프로세스가 쓰는 CPU 비율)와 `Runtime.getRuntime().totalMemory() - freeMemory()`(사용 중인 힙, MB로 환산). 둘 다 표준 JVM API — 외부 의존성이나 네이티브 라이브러리 불필요.

## 데이터 흐름

1. 게임 시작 → `CubeClientModClient.onInitializeClient()`가 `ConfigStore`, `FeatureRegistry`(FPS·속도·CPS·성능 등록), 인메모리 설정 캐시를 만들고 `HudLayerRegistrationCallback`에 CubeClient 레이어 하나를 등록.
2. 매 프레임 → 레이어가 켜진 `PositionedHudFeature`를 순회, 캐시에서 `positionOr(id, feature.defaultPosition())`로 위치를 얻어 그린다.
3. 플레이어가 타이틀/일시정지 화면에서 "클라이언트 설정" → "HUD 조절" 탭 클릭 → `HudEditorScreen` 진입, `ConfigStore.load()`로 현재 설정을 읽는다.
4. 드래그 → 인메모리로만 좌표 갱신, 마우스를 뗀 시점에 `ConfigStore.save()` + 캐시 갱신.
5. "나가기" → `ModListScreen`으로 복귀, 다음 프레임부터 HUD가 새 위치/크기로 그려짐(이미 갱신된 캐시를 레이어가 읽으므로 즉시 반영 — B0에서 확인된 "토글 즉시 반영" 요구사항과 같은 성질).

## 오류 처리

- `getProcessCpuLoad()`가 초기 몇 틱 동안 `-1.0`(아직 측정 안 됨, JVM 문서화된 동작)을 반환할 수 있음 — `PerformanceDisplay`는 음수면 "측정 중"으로 표시하고 크래시하지 않는다.
- `HudPosition`이 클램프 범위를 벗어난 값을 가진 손상된 설정 파일을 읽는 경우, `HudPosition.of(...)` 팩토리로만 생성하고 record 컴팩트 생성자에서도 같은 클램프를 적용해 역직렬화 경로(Gson이 정적 팩토리를 안 거치고 캐노니컬 생성자를 직접 호출함)로 들어오는 값도 안전 범위로 강제한다.
- `HudEditorScreen`에서 리사이즈 핸들 드래그 중 `scale`이 범위를 벗어나려 하면 시각적으로도 0.5~3.0에서 멈추도록(핸들이 그 이상 안 움직이는 것처럼 보이도록) 클램프 — 저장 시점에만 클램프하면 핸들이 마우스보다 먼저 멈춰야 하는데 실제로는 안 멈춰서 어색해 보이는 것을 막는다.

## 테스트

- `HudPosition`: 클램프 경계값(음수, 1.0 초과, scale 0/음수/3.0 초과) 순수 로직 테스트.
- `ModConfig`: `positions` 필드에 대해 B0의 `ConfigStoreTest`와 같은 패턴 반복 — 필드 자체가 없는 파일, `positions`만 있고 다른 필드가 없는 파일, 불변성 확인.
- `FeatureRegistry`/`Category`: 변경 없음, 기존 테스트 그대로.
- `CpsDisplay`의 "1초 롤링 윈도우" 로직: 실제 시간에 의존하지 않도록 시계를 주입 가능하게 만들어(예: `Supplier<Long> clockMillis` 생성자 인자) 순수 로직으로 테스트 — 실제 1초를 기다리는 테스트는 만들지 않는다.
- `SpeedDisplay`의 델타 계산: 좌표 두 쌍을 넣고 m/s 계산이 맞는지 순수 로직 테스트(Minecraft 엔티티 없이).
- `HudEditorScreen`/HUD 레이어 렌더링/실제 마우스 드래그: B0과 동일한 이유로 유닛 테스트 대상 아님 — 실기기 수동 검증(Task로 명시).
- 실기기 수동 검증 항목: HUD 조절 탭 진입 → 드래그 이동 → 리사이즈 → 나가기 → 게임 재시작 후 위치 유지 확인. 창 크기를 바꿔보고 비율 기반 좌표가 상대 위치를 유지하는지 확인. 모드 목록 스크롤이 실제로 카드 6개 이상일 때(HUD 4개 이상 등록된 상태에서) 동작하는지 확인 — B0에서 카드 1개라 못 봤던 문제.

## B1 실기기 검증에서 바뀐 것

설계 이후 실기기 테스트로 발견된 문제를 그때그때 고치면서, 아래 세 곳은 이 문서가 원래 서술한 구현과 실제 코드가 달라졌다. 원래 절은 "처음에 어떻게 설계했는가"의 기록으로 그대로 두고, 실제 동작은 여기 정리한다.

- **CPS 표시** (§ B1이 추가하는 기능 3개): `while (client.options.attackKey.wasPressed())`로 큐를 비우는 방식은 마인크래프트 자신도 매 틱 같은 큐를 먼저 소모해 CPS가 항상 0으로 보이는 문제가 있어 폐기했다. 이후 틱(20Hz) 단위로 `isPressed()`를 샘플링해 눌림 시작(edge)만 잡는 방식으로 바꿨지만, 이마저 사람의 클릭 속도보다 해상도가 낮아 실제 CPS를 절반가량 과소 측정했다. 최종적으로는 같은 edge-detection을 프레임 단위(`render()`, 보통 60Hz 이상)로 옮겨 해상도 문제를 해결했다 — `CpsDisplay.render()` 참고.
- **모드 목록 스크롤** (§ 모드 목록 스크롤): "그리드 영역 밖으로 나간 카드는 `context.enableScissor(...)`로 잘라내고"는 화면 전체(`super.render()` 전체)에 스캐너를 씌우는 것으로 처음 구현됐으나, 이는 그리드 위에 그려지는 탭 행과 검색창까지 함께 잘라내 보이지 않게 만드는 버그였다(클릭은 여전히 가능했음). 지금은 `ModListScreen`이 스캐너를 전혀 쓰지 않고, 대신 `FeatureCard.renderWidget()`이 카드 하나의 그리기 호출만 감싸는 스캐너를 그 위젯 안에서 열고 닫는다 — 화면 전체가 아니라 `visibleTop` 경계에 걸친 카드 자신의 튀어나온 부분만 잘라낸다.
- **성능 표시 RAM** (§ B1이 추가하는 기능 3개): "사용 중인 힙, MB로 환산"으로 서술했지만, 실제로는 F3 디버그 화면과 같은 기준으로 맞추기 위해 사용량을 최대 힙(`Runtime.maxMemory()`, `-Xmx`) 대비 백분율로 표시한다(`-Xmx` 미설정 시 `Long.MAX_VALUE`가 반환되어 나눗셈이 무의미해지므로, 그 경우엔 사용량 자체를 100%로 표시). `PerformanceDisplay.formatLine()` 참고.

## 전역 제약 (B0에서 이어짐, 계속 유효)

- Loom/Yarn/Loader/Fabric API 버전 번호 하드코딩 금지 — `gradle.properties`만 참조.
- Mixin 사용 금지.
- 색상은 `Theme` 상수만 사용.
- 토글/설정 변경은 즉시 반영.
- 알 수 없는 설정 id는 무시(에러 아님).
