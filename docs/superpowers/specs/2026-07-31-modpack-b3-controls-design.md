# Sub-project B3: 조작 (Toggle Sprint, C키 Zoom) — 설계

## 목표

기존 모드 목록 화면(`Category.CONTROL`, B0에서 이미 만들어짐)에 조작 기능 두 개를 추가한다: 한 번 누르면 계속 유지되는 Toggle Sprint, C키를 누르고 있는 동안 고정 배율(4배)로 화면을 당겨보는 Zoom(감도도 같이 줄어듦).

새 렌더링 프레임워크는 필요 없다 — 둘 다 화면에 좌표를 갖지 않는 순수 조작 기능이라 `PositionedHudFeature`가 아니라 `Feature`를 직접 구현한다. Zoom은 이 모드 최초로 **커스텀 키바인딩**을 등록하는 기능이다.

## 범위 밖 (B3에서 안 하는 것)

- Toggle Sneak — 사용자가 명시적으로 제외(웅크리기 없이 달리기만).
- 두 기능을 하나의 통합 기능으로 묶는 것 — Toggle Sprint와 Zoom은 서로 무관한 별개 기능, 각자 독립적으로 켜고 끈다.
- Zoom의 시네마틱 카메라 이펙트(부드러운 FOV 보간 애니메이션 등) — 즉시 전환/즉시 복원만.
- **Zoom 중 배율 조절(휠이든 다른 키든)** — 마우스 휠로 조절하려 했으나 이 Fabric API 버전엔 게임플레이 중 스크롤을 읽는 공개(비-믹스인) 이벤트가 없어서(실제 `javap`로 확인: `Mouse.onMouseScroll`은 private, `ScreenMouseEvents`는 화면이 열려 있을 때만 동작) 불가능했다. 다른 키로 단계 전환하는 대안도 검토했지만, 사용자가 **고정 배율 하나로 충분**하다고 확정 — 배율 조절 자체를 범위 밖으로 뺐다.
- 스프린트가 안 되는 상황(허기 부족, 웅크리는 중 등)을 우리 기능이 억지로 뚫는 것 — 바닐라의 판정을 존중한다.

## 아키텍처

### `ToggleSprint`

```
Feature 구현, Category.CONTROL, PositionedHudFeature 아님(화면 요소 없음)
```

- 새 키바인딩을 만들지 않고 바닐라의 기존 달리기 키(`client.options.sprintKey`)를 그대로 읽는다.
- `isPressed()` + 직접 edge 감지로 눌림 순간만 잡아 내부 `sprintOn` boolean을 토글한다. `wasPressed()`(소모형)는 절대 쓰지 않는다 — B1에서 CPS가 겪은 정확히 같은 종류의 레이스가 재현될 수 있음.
- **B1/B2의 "프레임 단위로 폴링해야 한다"는 교훈은 이 기능엔 그대로 적용하지 않는다** — 그 교훈은 CPS·Combo처럼 클릭 "횟수"를 정확히 세야 하는 기능에서 나온 것이고, 토글 키는 한 번의 누름-뗌을 놓치지만 않으면 되는 단발성 이벤트라 20Hz 틱 해상도로도 사람이 물리적으로 누르고 있는 시간(보통 100ms 이상) 안에서 놓칠 가능성이 매우 낮다. 그래서 edge 감지와 스프린트 강제 적용을 **같은** `ClientTickEvents.END_CLIENT_TICK` 리스너 안에서 함께 처리한다 — 화면이 없는 이 기능을 위해 별도의 프레임 단위 훅을 새로 찾을 필요가 없어진다.
- 매 틱 끝, 기능이 켜져 있고 `sprintOn`이 true면 `client.player.setSprinting(true)`를 강제 호출한다(edge 감지 다음 줄에서 바로).
- **왜 이게 되는지(가설, 검증 필요)**: 바닐라 자신도 매 틱 `sprintKey`를 폴링해서 "안 누르고 있으면 스프린트 해제"로 되돌리려 한다. `END_CLIENT_TICK`은 그 바닐라 처리가 이미 끝난 뒤, 같은 틱의 가장 마지막에 실행되는 훅이므로, 우리가 그 직후에 다시 `setSprinting(true)`를 쓰면 그 틱에서는 우리 값이 최종적으로 남는다 — 결과적으로 "누르고 있지 않아도 계속 스프린트"가 된다. **이 실행 순서 가정은 아직 실제 코드/실기기로 확인되지 않았다.** 계획 작성 단계에서 반드시 실컴파일 또는 실기기로 검증하고, 순서가 다르면(우리 리스너가 바닐라보다 먼저 돈다면) 대안을 다시 설계해야 한다.
- 순수 테스트 가능한 부분: 토글 상태 전환 로직 자체(`static boolean nextToggleState(boolean current, boolean isDown, boolean wasDown)` 형태로 뽑아서 edge 판정만 순수 함수로 검증 — CpsDisplay/ComboCounter와 같은 패턴).

### `ZoomKey`

```
Feature 구현, Category.CONTROL, PositionedHudFeature 아님
```

- 이 모드 최초의 커스텀 키바인딩. `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(KeyBinding)`(실제 존재 확인됨, 아래 검증 상태 참고)으로 새 `KeyBinding`을 만들고 기본 키를 C(GLFW 키코드)로 둔다.
- 누르고 있는 동안: `MinecraftClient.options.getFov()`/`getMouseSensitivity()`(둘 다 `SimpleOption<T>`, `getValue()`/`setValue(T)` 실제 존재 확인됨)의 원래 값을 기억해둔 채로 **고정 배율 4배**로 줄인다. 뗄 때(또는 기능이 꺼지거나 다른 화면이 열릴 때) 원래 값으로 즉시 복원한다.
- 감도도 같은 4배 비율로 줄인다 — OptiFine Zoom의 표준 동작(화면이 확대된 만큼 시점 회전도 같이 정밀해짐).
- 배율 조절(휠이든 다른 키든)은 없다 — 위 "범위 밖" 참고. 마우스 휠은 항상 바닐라 기본 동작(단축슬롯 전환) 그대로 둔다 — 아무것도 가로채지 않으므로 건드릴 코드 자체가 없다.

## 데이터 흐름

두 기능 다 B0의 `FeatureRegistry`/`ModListScreen`에 등록되는 것 외에 별도 배선이 없다 — 켜고 끄는 저장·불러오기는 기존 `ModConfig.enabled` 맵을 그대로 쓴다(위치 정보가 필요 없으니 `positions` 맵은 안 씀). `ToggleSprint`는 자체 `ClientTickEvents.END_CLIENT_TICK` 리스너를 추가 등록(SpeedDisplay/ComboCounter와 같은 패턴). `ZoomKey`도 `zoomKey.isPressed()`를 매 틱 확인해 FOV·감도를 즉시 세팅/복원하는 것으로 충분하다 — 휠 조절이 빠지면서 프레임 단위로 반응해야 할 이유(부드러운 배율 변화 등)가 없어졌으므로, `ToggleSprint`와 같은 `ClientTickEvents.END_CLIENT_TICK` 리스너로 통일한다.

## 오류 처리

- `client.player == null`(월드 진입 전/이탈 후 과도기) — 두 기능 다 그 프레임/틱은 아무 것도 안 하고 조용히 스킵, 크래시 없음.
- 스프린트가 바닐라 규칙(허기 부족, 웅크림 등)으로 막히는 상황 — 우리가 강제로 뚫지 않는다. `setSprinting(true)`를 호출해도 바닐라의 나머지 로직이 실제 이동 속도에 반영 안 하면 그게 정상 동작.
- 줌 도중 기능이 꺼지거나(모드 목록에서 토글) 다른 화면(일시정지 메뉴 등)이 열리는 경우 — FOV·감도가 반드시 원래 값으로 복원되어야 한다. 복원을 놓치면 "줌에 걸려서 안 풀리는" 사용자 체감 버그가 생기므로, 이 경로는 실기기 검증에서 명시적으로 확인한다.

## 테스트

- 순수 로직 유닛 테스트: `ToggleSprint`의 edge 감지/토글 상태 전환 함수.
- FOV/감도 저장·복원, 실제 키 입력 감지는 `MinecraftClient.options` 등 Minecraft 클래스에 직접 묶여 유닛 테스트 불가 — B0~B2와 같은 이유로 실기기 수동 검증 대상.
- 실기기 검증 항목: 스프린트 키 뗀 후에도 계속 달리는지 / 다시 누르면 멈추는지 / 허기 부족 등 바닐라 제약은 그대로 존중되는지(무리하게 뚫지 않는지) / C키 누르는 동안 화면이 4배로 줄어들고 떼면 즉시 원래대로 돌아오는지 / 마우스 휠(단축슬롯 전환)이 줌 중에도 평소처럼 동작하는지(우리가 아무것도 안 건드리므로 당연히 그래야 함) / 감도도 같이 줄어 정밀 조준이 되는지 / 일시정지 메뉴 등을 줌 도중 열어도 FOV/감도가 걸리지 않고 복원되는지.

## 검증 상태

이 스펙에 나온 API는 브레인스토밍 직후 전부 실제 jar를 `javap`로 뜯어 확인했다(추측 아님): `KeyBindingHelper.registerKeyBinding(KeyBinding)`(fabric-key-binding-api-v1), `GameOptions.getFov()`/`getMouseSensitivity()`(둘 다 `SimpleOption<T>` 반환), `SimpleOption.getValue()`/`setValue(T)`, `Entity.setSprinting(boolean)`/`isSprinting()`, `GameOptions.sprintKey`(공개 필드). 마우스 휠 가로채기가 불가능하다는 것도 이 과정에서 확인됐다(`Mouse.onMouseScroll`이 private, `ScreenMouseEvents`는 화면 열림 상태 전용) — 그래서 위 "범위 밖"에 배율 조절을 뺐다.

남은 위험 하나: `ToggleSprint`가 `END_CLIENT_TICK`에서 바닐라의 자체 스프린트 판정을 실제로 이기는지(실행 순서)는 스크래치 컴파일만으로는 확인 불가능한 런타임 동작이라 계획 실행 중 실기기로 검증한다.

## 전역 제약 (B0~B2에서 이어짐, 계속 유효)

- Loom/Yarn/Loader/Fabric API 버전 번호 하드코딩 금지.
- Mixin 사용 금지 — 이번 두 기능도 예외 없음. Zoom의 배율 조절을 포기한 것도 이 제약 때문(마우스 휠 가로채기엔 mixin 없이 되는 공개 API가 없었음).
- 색상은 해당 없음(HUD 요소 없음).
- 토글/설정 변경은 즉시 반영.
- 알 수 없는 설정 id는 무시.
- vanilla 키를 폴링할 땐 `wasPressed()`가 아니라 `isPressed()`+직접 edge 감지 — B1/B2에서 반복 확인된 교훈. 프레임 단위 폴링은 클릭 "횟수"를 정확히 세야 하는 기능(CPS·Combo)에서 필요했던 것이고, B3의 두 기능은 단발성 누름/뗌만 잡으면 되므로 틱 단위로 충분하다(위 아키텍처 절 참고) — 이 프로젝트의 표준이 "무조건 프레임 단위"가 아니라 "기능 성격에 맞게 판단"임을 보여주는 사례로 남겨둔다.
