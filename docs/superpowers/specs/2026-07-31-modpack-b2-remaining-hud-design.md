# Sub-project B2: 나머지 HUD 기능 — 설계

## 목표

B1이 만든 HUD 프레임워크(`PositionedHudFeature`, `HudRenderUtil`, `HudPosition`, `CachedConfig`, `HudEditorScreen`, `ModListScreen`의 스크롤·"HUD 조절" 탭)를 그대로 재사용해서 로드맵상 남은 HUD 기능 4개를 추가한다: 리소스팩 표시, 갑옷·도구 내구도 표시, 서버 접속 시 핑 표시, Combo Counter.

새 아키텍처는 없다 — B2는 이미 검증된 프레임워크에 기능만 얹는 서브프로젝트다. 유일한 프레임워크 변경은 `HudRenderUtil`의 콜백을 텍스트 전용에서 범용으로 넓히는 것(아래 참조).

## 범위 밖 (B2에서 안 하는 것)

- 미니맵, 죽은 위치 표시 — B4.
- Toggle Sneak/Sprint, C키 Zoom — B3.
- 서버 리스트 자체 — B5. (이번 "핑 표시"는 접속 중인 서버 하나의 핑만 다룬다. 서버 목록 화면과는 무관.)
- Combo Counter의 데미지량 표시, 크리티컬 판정, 콤보별 사운드/이펙트 — 순수 히트 카운트+리셋만.
- 내구도 HUD의 경고 색상(예: 내구도 낮으면 빨간색) — B2는 순수 표시만, 색상은 항상 `Theme.TEXT`.

## 아키텍처

### `HudRenderUtil` — 텍스트 전용에서 범용으로

현재 `HudRenderUtil.drawScaledText(DrawContext, HudPosition, TextDrawer)`는 콜백 안에서 텍스트만 그린다고 가정한 이름이다. `DurabilityDisplay`는 같은 콜백 안에서 아이템 아이콘도 그려야 하는데, 콜백 시그니처 자체(`(DrawContext, int x, int y) -> void`)는 이미 내용에 대해 아무 제약이 없다 — 이름만 좁았다. 메서드와 인터페이스 이름을 일반화한다:

```java
public final class HudRenderUtil {
    private HudRenderUtil() {}

    public static void drawScaled(DrawContext context, HudPosition pos, ScaledDrawer drawer) {
        // 본문은 기존 drawScaledText와 동일 — 로직 변경 없음, 이름만 바뀜
    }

    @FunctionalInterface
    public interface ScaledDrawer {
        void draw(DrawContext context, int x, int y);
    }
}
```

`FpsDisplay`/`SpeedDisplay`/`CpsDisplay`/`PerformanceDisplay` 4곳의 호출부(`drawScaledText` → `drawScaled`, `TextDrawer` → `ScaledDrawer`)를 같이 고친다. 로직 변경이 전혀 없는 순수 리네임이라 위험 낮음.

**검증 필요(실기기 전 컴파일로 1차 확인, 실기기로 2차 확인)**: 아이템 아이콘(`DrawContext.drawItem`)이 텍스트와 같은 매트릭스 push/scale/pop 안에서 의도대로 확대·축소되는지는 이 프로젝트에서 처음 쓰는 조합이라 실제로 그려봐야 안다. 깨지면(예: 아이콘만 스케일 안 먹음) `DurabilityDisplay.render()`가 아이콘은 스케일 밖에서, 텍스트만 스케일 안에서 그리는 대안으로 조정한다 — Task 단계에서 실컴파일로 우선 확인.

### `ResourcePackDisplay`

```java
public class ResourcePackDisplay implements PositionedHudFeature {
    @Override public HudPosition defaultPosition() { return HudPosition.of(0.01, 0.21, 1.0); }
    @Override public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = formatLine(client.getResourcePackManager().getEnabledProfiles());
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
    public static String formatLine(Collection<ResourcePackProfile> enabled) { /* 아래 참조 */ }
}
```

- `MinecraftClient.getResourcePackManager().getEnabledProfiles()` — `Collection<ResourcePackProfile>`, 실제 확인됨.
- `ResourcePackProfile.getDisplayName()` — `Text` 반환, `.getString()`으로 순수 문자열 추출.
- 바닐라 기본 팩("vanilla")은 목록에 포함되지만 사용자가 딱히 "리소스팩 적용중"이라 여기지 않으므로, `getId()`가 `"vanilla"`인 프로필은 표시에서 제외한다.
- 표시 없는(바닐라만 켜진) 상태는 `"리소스팩 없음"`으로 표시 — 빈 문자열이나 공백 렌더링보다 사용자가 "이게 꺼진 건가 로딩중인가" 헷갈리지 않게.
- 여러 개 켜져 있으면 쉼표로 나열(`String.join(", ", ...)`) — 대부분 1개, 드물게 여러 개.

**테스트**: `formatLine`은 `ResourcePackProfile`이 Minecraft 클래스라 순수 유닛 테스트로 직접 못 만든다. 대신 이름 문자열 리스트를 받는 순수 버전으로 한 단계 더 쪼갠다:

```java
public static String formatLine(List<String> displayNames) { /* "vanilla" 필터링은 호출부에서 이미 끝난 상태로 받는다 */ }
```

`render()`가 `ResourcePackProfile` 목록을 이름 문자열 리스트(바닐라 제외)로 변환한 뒤 이 순수 메서드를 호출 — 변환 로직 자체는 한 줄짜리라 별도 테스트 불필요, 포맷팅(빈 목록 → "리소스팩 없음", 여러 개 → 쉼표 나열)만 테스트한다.

### `DurabilityDisplay`

```java
public class DurabilityDisplay implements PositionedHudFeature {
    @Override public HudPosition defaultPosition() { return HudPosition.of(0.01, 0.26, 1.0); }
    @Override public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        // 배열 인덱스(PlayerInventory.getArmorStack(int))를 추측해서 쓰지 않는다 — 이름 기반
        // getEquippedStack(EquipmentSlot)이 있으므로 순서를 오해할 여지 자체를 없앤다.
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
                if (stack.isEmpty() || !stack.isDamageable()) continue;
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

- `LivingEntity.getEquippedStack(EquipmentSlot)` — `EquipmentSlot.HEAD`/`CHEST`/`LEGS`/`FEET`/`MAINHAND` 이름으로 직접 조회. 배열 인덱스 순서를 추측할 필요가 없다(실제 `javap`로 존재 확인됨). 화면 표시 순서는 위 코드의 나열 순서 그대로(머리→가슴→다리→발→도구), 자연스러운 위→아래.
- `ItemStack.isDamageable()` — 내구도 개념이 없는 아이템(예: 돌 블록을 손에 쥔 경우)은 건너뜀. 장착 안 한 빈 슬롯(`isEmpty()`)도 건너뜀.
- 표시값 = `getMaxDamage() - getDamage()`(남은 내구도, 최대값 대비 숫자 아님 — 사용자 확정).
- 세로 배치, 한 줄에 아이콘+숫자.

**테스트**: `render()` 전체가 `ItemStack`/`DrawContext` 등 Minecraft 클래스에 묶여있어 유닛 테스트 불가 — B1의 다른 화면 렌더링 코드와 동일한 성격, 실기기 수동 검증 대상.

### `PingDisplay`

```java
public class PingDisplay implements PositionedHudFeature {
    @Override public HudPosition defaultPosition() { return HudPosition.of(0.01, 0.31, 1.0); }
    @Override public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isInSingleplayer() || client.player == null || client.getNetworkHandler() == null) return;
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry == null) return;
        String text = formatLine(entry.getLatency());
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
    public static String formatLine(int latencyMillis) { return latencyMillis + "ms"; }
}
```

- `MinecraftClient.isInSingleplayer()` — 싱글플레이면 render()가 아무것도 안 그림(사용자 확정: 자동으로 숨김). `HudEditorScreen`에서 "켜진 기능"으로 잡혀 조절 화면엔 나타나지만, 실제 플레이 화면에서 싱글플레이 중엔 텍스트가 안 그려지는 것 — 토글 자체가 꺼지는 게 아니라 렌더링만 조건부.
- `ClientPlayNetworkHandler.getPlayerListEntry(UUID)` — 내 UUID로 내 자신의 탭 목록 엔트리를 찾음, `PlayerListEntry.getLatency()`가 ms 단위 핑.
- `entry`가 `null`인 극초반(접속 직후 탭 목록 아직 안 옴) 프레임엔 그냥 안 그림 — 에러 아님, 다음 프레임에 자연히 채워짐.

**테스트**: `formatLine(int)`만 순수 로직, 그 외 전부 Minecraft 결합이라 수동 검증.

### `ComboCounter`

가장 복잡한 기능 — 서버가 데미지를 판정하므로 클라이언트 모드는 "맞았다"를 직접 알 수 없다. 믹스인 금지 제약 안에서 공개 필드만으로 감지한다.

**감지 메커니즘** (실제 `javap`로 확인된 공개 필드만 사용):
- `MinecraftClient.targetedEntity` — 조준 중인 엔티티(공개 필드).
- `LivingEntity.hurtTime` — 데미지를 받으면 서버 동기화로 0에서 양수로 바뀌는 공개 필드(바닐라가 빨간 피격 이펙트를 그리는 데 쓰는 바로 그 필드).

공격키를 누른 순간의 조준 대상을 "대기 중인 스윙"으로 기록해두고, 그 대상의 `hurtTime`이 이후 몇 틱 안에 0→양수로 바뀌면 "내 공격이 맞았다"로 판정한다(네트워크 왕복 지연 때문에 클릭한 바로 그 틱이 아니라 몇 틱 뒤에 반영될 수 있어 감지 창을 둠). 창 안에 안 바뀌면 그냥 빗나간 것으로 보고 버림(리셋 사유는 아님).

```java
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
        if (client.player == null) return;

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

    @Override public HudPosition defaultPosition() { return HudPosition.of(0.01, 0.36, 1.0); }
    @Override public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = "콤보 " + combo;
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }
}
```

**리셋 규칙 확정**(사용자 확정): 3초간 명중 없으면 리셋 **그리고** 내가 맞으면 즉시 리셋. 몬스터에게 맞아도 유저에게 맞아도 동일하게 리셋(대칭 — `client.player.hurtTime`은 데미지 원인을 구분하지 않는 단일 필드이므로 구분해서 처리할 방법도 없고, 구분할 이유도 없음).

**테스트**: `shouldResetForTimeout(long, long)`만 순수 로직으로 테스트(경계값: 정확히 3000ms, 2999ms, 3001ms). 히트 감지 메커니즘 자체(`onTick`)는 `MinecraftClient`/`LivingEntity`에 묶여 테스트 불가 — B2에서 가장 리스크가 높은 부분이라 실기기 검증에서 **PvE(몹 때리기)와 PvP(다른 플레이어) 둘 다** 확인해야 한다.

## 데이터 흐름

기존 B1과 동일 — `CubeClientModClient`의 `HudLayerRegistrationCallback` 레이어가 매 프레임 켜진 `PositionedHudFeature`를 순회해 그린다. 새 기능 4개는 그 등록 목록에 추가되는 것 외에 별도 배선 없음. `ComboCounter`만 자체 `ClientTickEvents.END_CLIENT_TICK` 리스너를 추가로 등록(SpeedDisplay/CpsDisplay와 같은 패턴).

## 오류 처리

- `client.player == null`(월드 진입 전/이탈 후 과도기 프레임) — `DurabilityDisplay`/`ComboCounter`는 그냥 아무것도 안 그리고 리턴, 크래시 없음.
- `PingDisplay`의 `entry == null`(탭 목록 아직 안 옴) — 마찬가지로 조용히 스킵.
- `ComboCounter`의 `pendingTarget`이 감지 창 안에 사라지거나(`isRemoved()`) 죽으면 — 콤보 자체는 안 끊고 그냥 그 스윙만 버림(죽인 경우도 명중이었을 수 있지만, "죽기 직전 hurtTime 갱신이 감지 창 안에 도착하냐"는 타이밍 문제이지 로직 결함이 아님 — 놓치면 그냥 그 한 번만 안 세어짐, 콤보 자체는 유지).
- 감지 창(10틱)이 끝나기 전에 다른 대상을 공격하면 `pendingTarget`이 새 대상으로 덮어써져 이전 스윙의 명중 여부는 더 이상 추적하지 않는다 — 빠른 대상 전환 시 드물게 한 번 덜 세어질 수 있음(콤보가 틀어지거나 리셋되는 건 아니고, 카운트가 실제보다 1 적게 나올 수 있는 정도). 의도된 단순화.
- `ResourcePackDisplay`가 빈 목록(바닐라만)을 만나면 "리소스팩 없음"으로 표시, 에러 아님.

## 테스트

- 순수 로직 유닛 테스트: `ResourcePackDisplay.formatLine(List<String>)`, `PingDisplay.formatLine(int)`, `DurabilityDisplay`의 남은 내구도 계산(있다면 별도 static 메서드로 뽑아서), `ComboCounter.shouldResetForTimeout(long, long)`.
- 나머지(실제 렌더링, 아이템 아이콘, hurtTime 기반 감지)는 B0~B1과 동일한 이유로 유닛 테스트 대상 아님 — 실기기 수동 검증.
- 실기기 검증 항목: 리소스팩 켜고 끄면서 이름 반영 확인 / 갑옷·도구 장착·해제하며 내구도 숫자 갱신 확인, 아이콘이 스케일 조절해도 안 깨지는지 확인(HUD 조절 화면에서 크기 키워보기) / 서버 접속 시 핑 표시되고 싱글플레이 진입 시 사라지는지 확인 / **콤보: 몹 때리기, 다른 플레이어 때리기(PvP 서버 필요), 3초 대기 후 리셋 확인, 맞았을 때 즉시 리셋 확인 — 4가지 전부**.

## 검증 상태

이 스펙에 나온 API(`LivingEntity.getEquippedStack(EquipmentSlot)`, `ItemStack.isDamageable()/getMaxDamage()/getDamage()`, `DrawContext.drawItem()`, `MinecraftClient.getResourcePackManager().getEnabledProfiles()`, `ResourcePackProfile.getId()/getDisplayName()`, `MinecraftClient.isInSingleplayer()/getNetworkHandler()`, `ClientPlayNetworkHandler.getPlayerListEntry(UUID)`, `PlayerListEntry.getLatency()`, `MinecraftClient.targetedEntity`, `LivingEntity.hurtTime`, `Entity.isRemoved()`)는 전부 `mod/`에 스크래치 클래스를 만들어 `./gradlew.bat compileJava`로 실컴파일까지 통과시켜 확인했다(작성 후 삭제) — 추측 아님.

## 전역 제약 (B0~B1에서 이어짐, 계속 유효)

- Loom/Yarn/Loader/Fabric API 버전 번호 하드코딩 금지.
- Mixin 사용 금지 — Combo Counter도 예외 없음(위 감지 메커니즘이 그 제약 안에서 나온 설계).
- 색상은 `Theme` 상수만 사용.
- 토글/설정 변경은 즉시 반영.
- 알 수 없는 설정 id는 무시.
