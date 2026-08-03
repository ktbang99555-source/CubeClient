# CubeClient — 작업 인계 문서 (Antigravity용)

이 문서는 다른 AI 코딩 도구(지금까지는 Claude Code)가 작업하던 걸 넘겨받을 때 필요한 맥락을 모아둔 것이다. 이 프로젝트엔 자체 메모리 시스템이 없으므로, 여기 적힌 걸 이 세션의 유일한 사전 지식으로 취급할 것.

## 프로젝트가 뭔지

CubeClient — 페더클라이언트/루나클라이언트 스타일의 마인크래프트 런처.
- 로컬 저장소: `C:\Users\Skdji\OneDrive\Desktop\Projects\CubeClient`
- 공개 GitHub: https://github.com/ktbang99555-source/CubeClient
- **기본 브랜치는 `master` 하나뿐 — 이 프로젝트는 피처 브랜치를 안 쓰고 항상 master에 직접 커밋한다.** 이 관례를 바꾸지 말 것.

## 백업 (안심하고 실험해도 됨)

이 문서를 넘기기 직전 상태를 두 군데 백업해뒀다:
- Git 태그: `backup-before-antigravity-2026-08-03` (로컬+origin 둘 다 푸시됨)
- 로컬 전체 클론: `C:\Users\Skdji\OneDrive\Desktop\Projects\CubeClient-backup-2026-08-03`

뭔가 잘못되면 `git reset --hard backup-before-antigravity-2026-08-03` 또는 백업 클론 폴더로 완전히 되돌릴 수 있다. **단, 그렇다고 강제 push, 히스토리 재작성(rebase -i, commit --amend, filter-branch 등), 기존 파일 대량 삭제 같은 걸 자유롭게 해도 된다는 뜻은 아니다** — master가 곧 기록이므로 새 커밋을 쌓는 방식으로 작업할 것.

## 구조

```
backend/   Java 런처 백엔드 (버전 관리, 다운로드, Microsoft 인증, 실행 오케스트레이션)
ui/        Electron 프론트엔드 (렌더러 ↔ 메인 프로세스 ↔ backend 통신은 stdout JSON Lines만, 소켓/HTTP 없음)
mod/       Fabric 클라이언트 모드 — 인게임 HUD/조작/미니맵 등
docs/superpowers/specs/   지금까지의 설계 문서 (기능별 요구사항·아키텍처 결정)
docs/superpowers/plans/   지금까지의 구현 계획 (파일별 작업 목록, 검증된 API 시그니처 포함)
```

`docs/superpowers/plans/` 안의 각 계획 파일 앞부분엔 "검증된 API 시그니처" 섹션이 있는데, 실제 게임 jar를 `javap`로 직접 뜯어서 확인한 실제 API 목록이다 — 새 기능을 만들 때 참고할 가치가 크다.

## 환경 설정 (필수, 틀리면 그냥 안 됨)

- PATH의 `java`는 Java 8이라 이 프로젝트엔 못 쓴다.
- `backend/`는 **JDK 17** 필요: `C:\Users\Skdji\devtools\jdk17\jdk-17.0.19+10`
- `mod/`는 **JDK 21** 필요(마크 1.21.4 자체 요구): `C:\Users\Skdji\devtools\jdk21\jdk-21.0.11+10` — **중첩 디렉터리 정확히 지정할 것, 부모 `jdk21` 폴더만 주면 Gradle이 거부한다.**
- 모드 빌드: `mod/` 디렉터리에서 `JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" ./gradlew.bat build` (테스트만: `./gradlew.bat test`)
- Fabric Loom은 **1.10.2로 고정** — 최신 버전은 Gradle 데몬 자체가 Java 21이어야 해서 충돌할 수 있음, 임의로 올리지 말 것.
- 시스템 Gradle 없음, 항상 `gradlew.bat` 사용.
- 런처(Electron) 실행: `cd ui && CUBECLIENT_JAVA="C:/Users/Skdji/devtools/jdk17/jdk-17.0.19+10/bin/java.exe" npx electron .`
- 모드를 실제 게임에서 확인하려면: 빌드된 jar(`mod/build/libs/cubeclient-mod-0.1.0.jar`)를 `%APPDATA%\CubeClient\instances\<인스턴스폴더>\mods\`에 복사 — **인스턴스 폴더 이름이 `%APPDATA%\CubeClient\profiles.json`의 `"id"`와 다를 수 있다**(마크 버전 기준으로 폴더가 만들어지는 것으로 보임). `instances/` 밑 폴더를 직접 확인할 것, id만 믿지 말 것. 실제 크래시 로그·게임 로그는 `instances/<실제폴더>/logs/latest.log`와 `crash-reports/`에 있다(런처 자체의 `%APPDATA%\CubeClient\debug.log`는 Electron/백엔드 계층만 기록, 마크 크래시는 안 담김).

## 지금까지 완료된 것

- **Sub-project A**: 런처 인증·실행 파이프라인 (Microsoft OAuth, 버전 다운로드, Fabric 설치, 오프라인 실행 차단) — 완료.
- **Sub-project B (모드팩)**, 서브 단계별로:
  - B0: 모드 골격 + 인게임 모드 목록 화면
  - B1: HUD 프레임워크(위치·크기 드래그 편집기) + 속도/CPS/성능 표시
  - B2: 리소스팩 표시, 갑옷·도구 내구도, 서버 핑, Combo Counter
  - B3: 조작 — Toggle Sprint, C키 Zoom(진짜 8배, **이 프로젝트 유일한 믹신** 포함 — `GameRendererMixin`)
  - B4: 원형 지형 미니맵(M키 토글, 북쪽 고정, 반경 96블록, 엔티티 점, 방향 화살표)

전부 완료·실기기(실제 게임 실행)로 검증·`origin/master`에 푸시 완료 상태다. 최신 커밋은 `eb65f2d`.

## 다음 작업 후보 (아직 설계 안 됨, 처음부터 대화로 시작할 것)

- **B5 — 죽은 위치 표시**: 사용자가 먼저 제안한 아이디어는 "죽은 자리에 수직선(빔)을 그어서 표시"하는 것. 그 외엔 아직 아무것도 정해진 게 없다 — 화면에 텍스트로도 표시할지, 얼마나 오래 보일지, 다른 차원에서 죽으면 어떻게 할지 등 전부 사용자와 대화로 좁혀야 한다.
- **B6 — 서버 리스트·핑**: 아직 착수 전.

## 권장 작업 방식

지금까지 이 프로젝트는 항상 이 순서로 진행했다 — 규칙은 아니지만 지금까지 잘 맞았다:

1. **요구사항을 질문으로 좁힌다** — 바로 코드부터 쓰지 않는다. 특히 기능이 여러 개 얽혀 있으면(이번 B5도 "표시 방식"부터 "지속 시간"까지 여러 결정이 필요) 하나씩 물어서 확정.
2. **설계 문서를 쓴다** — `docs/superpowers/specs/YYYY-MM-DD-<주제>-design.md`. 아키텍처, 범위 밖(안 하는 것), 데이터 흐름, 에러 처리, 테스트 전략을 담는다.
3. **구현 계획을 쓴다** — `docs/superpowers/plans/YYYY-MM-DD-<주제>.md`. 파일별로 뭘 만들지, 실제 코드 예시, 어떻게 테스트할지까지 미리 다 적어둔다(추상적으로 "에러 처리 추가" 같은 자리표시자를 쓰지 않는다).
4. **작업 단위별로 구현 + 테스트**. 순수 로직(Minecraft 객체에 안 묶인 계산)은 유닛 테스트로 반드시 검증. 실제 게임 객체(World, Entity, BlockState 등)에 묶인 코드는 유닛 테스트가 안 된다 — 이건 정상이고, 실기기 검증으로 넘긴다.
5. **작게, 자주 커밋한다.** 메시지엔 "왜"를 담는다.
6. **반드시 실제 게임을 켜서 확인한다.** 컴파일 되고 테스트 통과했다고 "됐다"고 하지 말 것 — 렌더링·입력·월드 상호작용은 실행해봐야만 드러나는 버그가 계속 나왔다(아래 함정 목록 참고).

## 실전 함정 — 전부 실제로 겪고 고친 것들, 꼭 읽을 것

- **Minecraft/Fabric API를 절대 추측하지 말 것.** 메서드가 있을 것 같다/이런 시그니처일 것 같다는 감으로 코드를 쓰지 말고, 실제 Yarn 매핑된 jar를 직접 뜯어서 확인한다:
  ```
  javap -p -classpath "<yarn-mapped-jar-path>" net.minecraft.패키지.클래스명
  ```
  매핑된 jar는 보통 `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/...`에 있다(정확한 경로는 `mod/gradle.properties`의 `yarn_mappings` 버전에 따라 달라짐). **Boolean 이름의 API(`isPinned`, `isRequired` 등)도 이름만 보고 의미를 짐작하지 말 것** — 이름과 실제 동작이 다른 경우가 여러 번 있었다.
- **KeyBinding**: 바닐라가 이미 폴링하는 키(공격/채굴 등)엔 `wasPressed()`를 쓰면 안 된다(vanilla가 먼저 소모해버림) — `isPressed()`(비소모) + 직접 눌림 순간 검출을 쓸 것. 정확한 "횟수" 카운팅(CPS 등)이 필요하면 프레임 단위(`WorldRenderEvents` 등)로 폴링해야 한다 — 틱(20Hz)은 사람 클릭 속도를 못 따라간다. 반대로 단순 토글(누르면 상태 전환)은 틱 단위로 충분하다.
- **커스텀 키바인딩 등록 시 바닐라 기본 키와 겹칠 수 있다.** 실제 실행 중인 인스턴스의 `options.txt`를 확인해서 확실히 할 것. 겹치면 `InputUtil.isKeyPressed(windowHandle, keyCode)`로 GLFW 원시 키 상태를 직접 읽어 우회할 수 있다(`KeyBindingHelper.getBoundKeyOf()`로 재바인딩도 계속 따라가면서).
- **믹신은 원칙적으로 금지.** "공개 API로 정말 안 된다"는 걸 `javap`로 직접 확인한 근거와 사용자의 명시적 승인이 있을 때만, 최소 범위로 예외를 둔다. 쓰게 되면: 믹신 config의 `"package"` 필드는 그 패키지 **전체**를 자기 것으로 소유한다 — 그 안에 일반 브릿지/헬퍼 클래스를 같이 두면 `IllegalClassLoadError`로 월드 진입 즉시 크래시하는데, **컴파일/빌드/리프맵 검사로는 절대 안 잡히고 실제 실행해야만 드러난다.** 브릿지 클래스는 반드시 다른 패키지에.
- **`net.minecraft.util.math.ChunkPos`는 순수 값 타입처럼 보이지만 아니다.** static 초기화가 실제로 채워진 게임 레지스트리(`ChunkStatus.FULL` 등)를 참조해서, 게임이 부팅 안 된 순수 유닛 테스트 환경에서 인스턴스화하면 `IllegalArgumentException: Not bootstrapped`로 죽는다. 순수 로직/테스트 계층에선 직접 정의한 값 타입(`record ChunkCoord(int x, int z)` 같은)을 쓰고, 실제 `ChunkPos` 변환은 게임이 실제로 도는 지점에서만 할 것. **비슷하게 생긴 다른 클래스도 안전하다고 가정하지 말고 static 초기화 블록까지 `javap -c`로 확인할 것** — 예를 들어 `World.OVERWORLD`/`NETHER`는 안전했다(레지스트리 키 객체만 만들 뿐 채워진 레지스트리를 참조 안 함).
- **`MapColor.CLEAR`**(꽃·잔디·눈 쌓임 같은 장식 블록의 "색 없음" 지도색)의 **원본 색상값은 0(검정)이다.** 필터링 없이 그대로 렌더링하면 불투명 검은 사각형이 생긴다.
- **로드 안 된 청크를 그냥 조회하면 안 된다.** `World.getTopY(...)`를 로드 안 된 청크에 부르면 `getBottomY()`를 그대로 돌려주는 등 무의미한 값이 나온다 — 청크를 실제로 다루기 전에 `world.isChunkLoaded(x, z)`로 먼저 확인할 것.
- **HUD 요소의 위치/크기 편집 화면에서, 요소를 클릭만 해도(드래그 안 해도) 저장이 트리거된다.** 코드의 기본 위치를 바꿔도, 예전에 한 번이라도 그 화면에서 클릭된 적 있는 요소는 저장된 값이 우선이라 안 바뀐 것처럼 보인다 — 버그 아니라 설계대로지만 헷갈릴 수 있다.
- **매 프레임(초당 60~200회 이상) 무거운 작업을 하면 FPS가 눈에 띄게 깎인다** — OS 레벨 질의, 픽셀 단위 이미지 합성/텍스처 업로드 같은 건 반드시 틱(20Hz) 단위로 옮기거나 명시적으로 예산(예: 틱당 1개)을 둘 것. 렌더링 경로 안에 이런 작업이 남아있진 않은지 파일 전체를 다시 훑어서 확인할 것 — 부분적으로만 고치고 끝났다고 착각하기 쉬웠다.
- **같은 하드코딩된 상수(예: HUD 요소 크기 근사값)가 한 파일 안에서도 여러 곳에 따로 박혀있을 수 있다.** 하나 고치고 끝났다고 가정하지 말고 grep으로 다른 재사용처를 확인할 것.
- **캐시 무효화 조건을 세울 때 "종류가 같은가"와 "실제 인스턴스가 같은가"는 다른 축이다** — 예를 들어 차원 종류(오버월드/네더 등)가 같아도 월드 재접속·다른 서버 접속처럼 실제 객체가 바뀌는 경우가 있다. 둘 다 확인할 것.
- Gson은 record의 없는 필드를 null로 채운다 — 설정 파일 파싱 시 compact 생성자로 null을 정규화해둘 것(빈 `{}` 파일이 게임을 크래시시킨 전례 있음).

## 사용자 소통

- **응답은 한국어로.** 어떤 템플릿/스킬 문구든 영어 그대로 보여주지 말고 번역해서 전달할 것.
- 사용자는 이 프로젝트를 A(런처)부터 B0~B4(모드팩)까지 전부 실제로 실행해보며 함께 검증해왔다 — "됐다"는 보고보다 "직접 켜서 봐달라"는 요청을 자연스럽게 여긴다.
