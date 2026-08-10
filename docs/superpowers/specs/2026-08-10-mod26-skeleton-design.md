# Sub-project M0: `mod26` 뼈대 — 마인크래프트 26.1 Fabric 모드 스켈레톤 설계

## 배경

2026년에 Mojang이 마인크래프트 버전 번호 체계를 `1.x.x`에서 연도 기반(`26.1`, `26.2`, `26.3`...)으로 바꿨다. `26.1`부터는 게임 자체가 난독화(obfuscation)되지 않은 첫 버전이라 Fabric이 Yarn 매핑 지원을 끊고 Mojang 공식 매핑으로 전환했다 — 우리 `mod/`(1.21.4, Yarn 기반 Loom 1.10.2)와는 완전히 다른 빌드 툴체인(Java 25, Loom 1.15, Gradle 9.4.0, 새 `net.fabricmc.fabric-loom` 플러그인, 리매핑 없는 `jar`/`implementation`)이 필요하다.

사용자가 CubeClient 모드팩과 런처 둘 다 여러 마인크래프트 버전을 지원하도록 요청했고, 범위를 "26.x부터 바로(매핑 마이그레이션 포함)"로 정했다. 다만 기능 12개(믹신 1개 포함)를 전부 한 번에 포팅하는 건 리스크가 너무 커서, **새 툴체인 자체가 실제로 되는지 검증하는 빈 뼈대부터** 만들기로 했다 — 기능 포팅(M1~)과 런처의 버전별 모드 jar 선택(L1)은 별도 서브프로젝트로 미룬다.

## 목표

기능 0개, 로그 한 줄만 남기는 최소 Fabric 클라이언트 모드를 마인크래프트 26.1 + Java 25 + Loom 1.15 + Mojang 매핑 툴체인으로 빌드하고, 실제 게임에서 크래시 없이 로드되는 것을 실기기로 확인한다.

## 범위 밖 (M0에서 안 하는 것)

- 기존 12개 기능(HUD·조작·미니맵·죽은위치) 포팅 — 전부 다음 서브프로젝트(M1~)로 미룸.
- 믹신 마이그레이션 — 기능이 0개라 믹신도 0개, 해당 없음.
- 런처가 프로필별로 다른 모드 jar/Fabric API 버전을 고르게 하는 것(`LoaderInstaller.FABRIC_API_VERSION` 하드코딩 해소) — 별도 서브프로젝트(L1)로 미룸. 이번 실기기 검증은 손으로 만든 인스턴스/설정으로 우회한다.
- 모드 목록 화면·`FeatureRegistry`·`CachedConfig` 등 기존 `mod/`의 인프라 코드 재사용 — 이번엔 아무것도 등록할 게 없어서 필요 없다. 포팅해야 한다면 M1에서.
- `mod/`(1.21.4) 프로젝트에 대한 어떤 수정도 — 완전히 독립된 새 프로젝트만 추가한다.

## 아키텍처

`mod/`와 나란히 완전히 독립된 새 Gradle 프로젝트 **`mod26/`**를 리포지토리 루트에 추가한다. `mod/`가 지금 그렇듯 자체 `settings.gradle`/`build.gradle`/`gradle.properties`를 갖는 standalone 프로젝트로 둔다 — 멀티모듈로 묶지 않는다. 이유: 두 프로젝트는 서로 다른 게임 프로세스에서 돌기 때문에 묶을 실익이 없고, 묶으면 `mod/`의 빌드 설정에 영향을 줄 위험만 생긴다.

- **패키지명**: `com.cubeclient.mod` 그대로 재사용. 실제로 같은 JVM에 동시에 로드될 일이 없어(다른 인스턴스, 다른 프로세스) 충돌 없고, 나중에 M1에서 코드를 포팅할 때 "같은 모드의 다른 버전"이라는 개념이 패키지명에서도 드러난다.
- **모드 ID**: `cubeclient` 그대로. 서로 다른 인스턴스에 설치되므로 충돌 없음.
- **엔트리포인트**: `com.cubeclient.mod.CubeClientModClient`(`mod/`와 같은 클래스명) 하나만, `ClientModInitializer.onInitializeClient()`에서 SLF4J로 `"CubeClient (26.1) initialized"` 로그 한 줄만 남긴다. `FeatureRegistry`도, 모드 목록 화면도, 설정 파일도 없다 — 정말로 로그 한 줄이 전부다.
- **`fabric.mod.json`**: `client`용 엔트리포인트 하나, `depends`에 `fabricloader`(정확한 최소 버전은 계획 단계에서 실제 meta.fabricmc.net 조회로 확정 — 지금 조사에선 "0.18.4가 최신 안정"이라는 정보만 있고 최소 요구 버전은 미확인), `minecraft: "26.1"`(정확히 이 버전에 고정 — `mod/`가 1.21.4에 정확히 고정된 것과 같은 보수적 관례). Fabric API 의존은 "알려진 리스크" 섹션 참고.

## 빌드 툴체인 (계획 단계에서 실제 문서/API로 재검증 필요 — 지금은 조사한 요약)

- Java 25 툴체인 (JDK 25가 이 머신에 아직 없음 — Adoptium에 실제로 25 빌드가 있는지부터 확인 필요, 첫 태스크).
- Loom 1.15, Gradle 9.4.0 래퍼.
- 새 `net.fabricmc.fabric-loom` 플러그인 — 기존 `fabric-loom-remap`이 아니다. `modImplementation` → 표준 `implementation`, `remapJar` 태스크 → `jar` 태스크로 대체(26.1부터 게임이 난독화 안 되어 있어 리매핑 스텝 자체가 없어짐).
- 매핑은 Mojang 공식 매핑 — Loom의 정확한 Gradle 문법(`loom.officialMojangMappings()`류로 추정되나 실제 확인 안 됨)은 계획 작성 시 `docs.fabricmc.net` 원문으로 재검증.
- `mod26/gradle.properties`에 정확한 버전들을 `mod/gradle.properties`와 같은 방식으로 하드코딩하고 출처(URL)를 주석으로 남긴다 — 버전 번호를 추측하지 않는다는 이 프로젝트의 원칙을 그대로 따른다.

## "됐다"의 기준 + 검증

1. `mod26/` 디렉터리에서 `./gradlew build`가 성공한다.
2. 빌드된 jar를 런처의 기존 인스턴스에 손으로 넣어(또는 손으로 새 인스턴스를 만들어) 실제 마인크래프트 26.1 + Fabric으로 게임을 실행한다.
3. 실기기 확인 항목:
   - 게임이 크래시 없이 타이틀 화면까지 뜨는지.
   - 로그(`logs/latest.log` 등)에 `"CubeClient (26.1) initialized"`가 찍히는지.
   - (부수적으로) 런처의 기존 로더 설치·JRE 자동설치·에셋 다운로드 경로가 26.1에 대해 코드 변경 없이 이미 동작하는지, 아니면 어디서 막히는지 — 막히는 지점이 있다면 그게 다음 서브프로젝트(L1)의 실제 스코프를 정하는 데 쓰인다.

## 오류 처리

- 빌드가 실패하면(툴체인 버전 불일치, 매핑 API 오류 등) 계획의 해당 태스크에서 바로 원인을 규명하고 고친다 — 이 프로젝트의 "API 추측 금지, 항상 실제로 검증" 원칙을 그대로 적용(이번엔 대상이 Mojang 매핑 jar라 `javap`로 실제 이름을 바로 확인할 수 있어 오히려 Yarn 리매핑보다 검증이 쉬울 수 있다).
- 게임이 크래시하면 크래시 리포트/로그를 근거로 원인을 규명한다. 추측성 수정 금지.

## 테스트

- 순수 로직이 없다(기능 0개) — 유닛 테스트 대상이 없다. `mod/`처럼 JUnit 5 스캐폴딩(`build.gradle`의 test 블록)만 갖춰서 M1부터 바로 TDD를 시작할 수 있게 해둔다.
- "됐다"의 검증은 전부 빌드 성공 + 실기기 확인으로 이루어진다(위 섹션).

## 검증 상태 (계획 단계에서 실제로 확인해야 하는 것 — 브레인스토밍 조사로는 다 확정 못 함)

- **Fabric API가 마인크래프트 26.1용으로 실제 배포됐는지 미확인.** 없으면 이번 스켈레톤은 Fabric API 의존 없이(Fabric Loader 코어의 `ClientModInitializer` 엔트리포인트만으로) 진행한다 — Fabric API가 필요한 기능(`ScreenEvents`, `WorldRenderEvents` 등)은 M1부터의 문제이지 M0의 문제가 아니다.
- Loom의 Mojang 매핑 정확한 Gradle 문법.
- `fabricloader` 최소 버전 요구치의 정확한 값.
- Adoptium(또는 다른 배포처)에 JDK 25 빌드가 실제로 있는지, 있다면 정확한 다운로드 URL/체크섬.
- 새 `net.fabricmc.fabric-loom` 플러그인의 정확한 Gradle 플러그인 ID·버전 좌표.

## 전역 제약

- Loom/매핑/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `mod26/gradle.properties`만 참조한다(`mod/`와 같은 관례).
- 믹신 없음(기능 0개).
- `mod/`(1.21.4) 프로젝트는 이 서브프로젝트에서 절대 수정하지 않는다.
- API를 추측하지 않는다 — 실제 문서/실제 jar(`javap` 등)로 검증 후 사용한다.
