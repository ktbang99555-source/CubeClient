# CubeClient — 런처 코어 (Sub-project A) 설계

## 배경 및 목표

CubeClient는 Feather Client / Lunar Client 스타일의 마인크래프트 런처로, 계정 로그인/버전 관리/게임 실행을 담당하는 런처 코어 위에, 자체 제작한 Fabric 기반 편의 모드(미니맵, 리소스팩+FPS 오버레이, 서버리스트/핑 표시)를 얹어 제공하는 것을 목표로 한다.

전체 프로젝트는 세 개의 독립적인 하위 프로젝트로 나눈다.

- **A. 런처 코어** (본 문서의 범위) — Electron UI + Java 백엔드. 계정 로그인, 버전 매니페스트 처리, 게임 실행.
- **B. 최신 버전용 자체 Fabric 모드팩** (1.21.x 기준) — 미니맵, FPS/리소스팩 오버레이, 서버리스트/핑
- **C. 1.8.9(하이픽셀 대응, Legacy Fabric)용 자체 모드팩** — B와 동일한 기능을 레거시 구조에 맞게 별도 구현

A가 완성되어야 B/C를 실행/검증할 수 있으므로 A → (B, C) 순서로 진행한다. B와 C는 동시에 설계·개발하기로 결정했다.

지원 버전 범위: 1.20.x ~ 1.21.x(최신) 및 예외적으로 1.8.9(하이픽셀 대응). 런처 코어는 이 범위의 프로필을 모두 다룰 수 있어야 한다.

## 아키텍처

```
┌─────────────────────┐        stdin/stdout          ┌──────────────────────┐
│   Electron (UI)      │◄──── JSON Lines 스트림 ─────►│  Java 백엔드 (jar)     │
│  - 로그인 화면         │      child_process.spawn     │  - MS OAuth 처리       │
│  - 프로필/버전 선택     │                               │  - 버전 매니페스트      │
│  - 진행률 표시         │                               │  - 라이브러리/에셋 DL   │
│  - 실행/종료 버튼       │                               │  - Fabric/Legacy Fabric│
└─────────────────────┘                               │    로더 설치            │
                                                        │  - MC 프로세스 실행     │
                                                        └──────────────────────┘
```

- Electron은 UI(로그인, 프로필 선택, 진행률, 실행/종료)만 담당하며 게임 관련 로직을 갖지 않는다.
- Java 백엔드는 독립 jar로 빌드되어 Electron이 서브커맨드(`login`, `list-profiles`, `launch --profile <id>` 등)로 spawn한다.
- 백엔드는 표준출력으로 `{"type":"progress","stage":"...","percent":42}` 형태의 JSON 라인을 스트리밍하고, Electron이 이를 파싱해 UI를 갱신한다.
- 이 구조를 택한 이유: Lunar Client도 Electron+Java 조합이라 검증된 패턴이며, 로컬 포트/소켓 수명 관리 없이 프로세스 stdout 파이프만으로 통신이 가능해 단순하다.

## 데이터 흐름 & 프로필 모델

**저장 위치**: `%APPDATA%/CubeClient/`

```
├─ profiles.json        # 프로필 목록 (버전, 로더, 모드 구성)
├─ auth.json             # 암호화된 MS refresh token (Electron safeStorage)
├─ runtimes/             # 버전별 번들 JRE (8, 17+ 자동 다운로드·캐시)
├─ versions/ libraries/ assets/   # 마인크래프트 공용 리소스 (버전 매니페스트 기준)
└─ instances/<profileId>/          # 프로필별 격리 폴더 (mods, config, saves)
```

프로필은 MultiMC의 "인스턴스" 개념과 동일하다. 버전별로 폴더가 완전히 분리되어 있어 1.21 프로필과 1.8.9 프로필이 서로 간섭하지 않는다.

**프로필 예시**:

```json
{ "id": "latest-1.21", "mcVersion": "1.21.4", "loader": "fabric", "mods": ["minimap", "fps-hud", "serverlist"] }
{ "id": "hypixel-1.8.9", "mcVersion": "1.8.9", "loader": "legacyfabric", "mods": ["minimap", "fps-hud", "serverlist"] }
```

**실행 흐름**:

1. 앱 시작 → `auth.json` 확인 → 유효하면 자동 로그인, 아니면 MS 로그인 화면(디바이스 코드 방식) 표시
2. 홈 화면에 프로필 카드 목록 표시
3. Play 클릭 → Java 백엔드에 `launch --profile <id>` 전달
4. 백엔드: 버전 매니페스트 확인 → 부족한 라이브러리/에셋/JRE 다운로드(SHA1 체크섬 검증) → (Fabric/Legacy Fabric 프로필이면) 로더 및 모드 jar 설치 → JVM 인자 구성 → 마인크래프트 프로세스 실행
5. 진행 상황을 JSON 라인으로 스트리밍하고, 프로세스 종료 코드까지 Electron에 전달

## 오류 처리

- **로그인 실패** (네트워크/세션 만료): 명확한 에러 메시지와 재시도 버튼을 표시한다. 백엔드 에러는 `{"type":"error","stage":"auth","message":...}` 이벤트로만 전달되며 Electron 프로세스는 크래시하지 않는다.
- **다운로드 손상/중단**: 매니페스트의 SHA1 해시와 대조해 불일치 시 자동 재다운로드한다. 중단된 다운로드는 재시작 시 이어받는다.
- **Java 버전 불일치**: 1.20+는 Java 17+, 1.8.9는 Java 8이 필요하다. 백엔드가 프로필별로 맞는 JRE를 Adoptium API를 통해 `runtimes/`에 자동 다운로드하여 완전히 격리 관리하므로 사용자가 시스템 Java를 신경 쓸 필요가 없다.
- **게임 크래시**: stdout/stderr를 로그 파일로 저장하고 Electron에 "로그 보기" 버튼을 제공한다. 백엔드 프로세스가 죽어도 Electron은 "종료됨" 상태로 정상 표시된다.

## 테스트 전략

- **Java 백엔드**: 버전 매니페스트 파싱, 체크섬 검증, JVM 인자 조립 로직은 순수 유닛 테스트로 커버한다(다운로드는 HTTP 모킹). 실제 마인크래프트 실행은 CI에서 검증할 수 없으므로 자동화 테스트 범위에서 제외한다.
- **Electron**: 프로필 카드 렌더링, 진행률 UI 상태 전이는 컴포넌트 테스트로 커버한다. 실제 로그인+실행 전체 흐름은 정상 MS 계정을 이용한 실기기 수동 테스트로 검증한다.

## 범위 밖 (B, C 및 이후 단계)

- Fabric/Legacy Fabric 모드(미니맵, FPS/리소스팩 오버레이, 서버리스트·핑) 자체 구현은 Sub-project B, C에서 별도로 브레인스토밍한다.
- 코스메틱(망토/백팩), 서버 리스트 내 자동 핑 표시 고도화, 다버전(1.20.x 세부 버전 다중 지원) 확장 등은 A 완료 이후 필요에 따라 별도 스펙으로 다룬다.


---

## 개정 (2026-07-26): 1.8.9 및 Sub-project C 범위 제외

1.8.9를 실제로 실행해 보고 내린 결정이다. 구버전 마인크래프트는 네이티브 라이브러리(LWJGL의
`.dll`)를 **classifier 아티팩트**로 배포한다. 확인된 사실:

- 바닐라 1.8.9 매니페스트의 라이브러리 5개가 `downloads.classifiers`를 쓴다. 이 런처의 파서는
  `downloads.artifact`만 읽으므로 이들을 통째로 건너뛴다.
- Legacy Fabric의 `lwjgl-platform`은 classifier 없는 jar가 **아예 존재하지 않는다**
  (`-natives-windows` 등만 있음).

따라서 1.8.9를 띄우려면 classifier 파싱, natives jar 압축 해제, `-Djava.library.path` 전달이
모두 필요하다. 1.20.x~1.21.x는 LWJGL 3를 쓰고 natives가 일반 classpath jar라 이 문제가 없다.

**결정**: 지원 범위를 1.20.x~1.21.x로 좁히고, 1.8.9(하이픽셀)와 그것을 위한
**Sub-project C(Legacy Fabric 모드팩)를 제외**한다. Legacy Fabric 지원 코드도 제거했다 —
남은 범위는 업스트림 Fabric이 전부 커버하므로 죽은 코드이고, natives 문제로 깨진 것을 아는
코드를 남기면 오해를 부른다.

**남는 것**: A(런처 코어, 완료) + B(1.21.x용 자체 Fabric 모드팩).
