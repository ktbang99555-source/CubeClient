# Sub-project M0: `mod26` 뼈대 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기능 0개, 로그 한 줄만 남기는 최소 Fabric 클라이언트 모드를 마인크래프트 26.2 + Java 25 + Loom 1.17 + Mojang 공식(비난독화) 이름 툴체인으로 빌드하고, 실제 게임에서 크래시 없이 로드되는 것을 실기기로 확인한다.

**Architecture:** `mod/`(1.21.4)와 완전히 독립된 새 standalone Gradle 프로젝트 `mod26/`를 리포지토리 루트에 추가한다. 패키지명(`com.cubeclient.mod`)과 모드 ID(`cubeclient-mod`, `mod/`의 실제 `fabric.mod.json`에서 확인한 값 — 브레인스토밍 스펙의 "cubeclient"는 오기)는 재사용하되, 완전히 다른 빌드 파일 세트를 쓴다. `ClientModInitializer` 엔트리포인트 하나만 두고, `mod/`가 갖고 있는 빈 `main` 엔트리포인트(`CubeClientMod`)는 지금 아무도 안 쓰므로 이번엔 아예 선언하지 않는다(필요해지면 그때 추가).

**Tech Stack:** Fabric Loom 1.17-SNAPSHOT, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API `0.156.0+26.2`, Gradle 9.5.1, JDK 25(Temurin `jdk-25.0.4+7`).

## 범위 변경 — 브레인스토밍 스펙과 다른 점

스펙(`docs/superpowers/specs/2026-08-10-mod26-skeleton-design.md`)은 대상 버전을 "26.1"로 적었지만, 이 계획을 쓰면서 실제 API로 확인해보니:

- **26.1은 이미 26.1.1, 26.1.2로 패치됐고, 그 뒤 26.2가 나와서 지금 안정판 최신은 26.2다** (`piston-meta.mojang.com/mc/game/version_manifest_v2.json`의 `latest.release`가 `"26.2"`, 직접 확인).
- Fabric API 저장소(`maven.fabricmc.net`)에 `+26.1`을 정확히 태그한 빌드가 아예 없다 — `+26.1.2`만 있다.
- Fabric 공식 예제 모드 저장소(`github.com/FabricMC/fabric-example-mod`)의 **기본 브랜치 자체가 `26.2`**다.

스펙의 의도("새 툴체인이 실제로 되는지, 지금 시점 마인크래프트로 검증")를 살리려면 이미 두 번 지나간 "26.1"보다 지금 실제 안정판인 **26.2**를 대상으로 하는 게 맞다. 아키텍처·범위·"됐다"의 기준은 스펙 그대로이고, 버전 숫자만 26.2로 바뀐다.

## Global Constraints

- Loom/매핑/Loader/Fabric API 버전 번호를 하드코딩하지 않는다 — `mod26/gradle.properties`만 참조한다.
- 믹신 없음(기능 0개).
- `mod/`(1.21.4) 프로젝트는 이 서브프로젝트에서 절대 수정하지 않는다.
- API를 추측하지 않는다 — 이 계획의 모든 버전 번호·좌표·URL은 아래 "실제로 확인한 값" 표에 있는 것처럼 실제 API 응답/공식 저장소에서 직접 가져온 것이다. 추측이 필요한 지점이 나오면 Task 2의 실기기 검증에서 실제로 확인한다.

## 실제로 확인한 값 (추측 아님 — 이 계획 작성 시점에 직접 조회함)

```
# Fabric 메타 API로 확인 (2026-08-10)
$ curl https://meta.fabricmc.net/v2/versions/loader/26.2
→ 최신 안정 로더: net.fabricmc:fabric-loader:0.19.3 (mod/의 1.21.4용과 같은 버전)

$ curl https://meta.fabricmc.net/v2/versions/yarn/26.2
→ [] (Yarn 매핑 없음 — 26.1부터 게임이 비난독화되어 매핑 자체가 불필요)

# Mojang 버전 매니페스트로 확인
$ curl https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
→ "latest": {"release": "26.2", "snapshot": "26.3-snapshot-7"}
→ id "26.2"가 정확히 존재 (patch 없는 정식 릴리스)

# Fabric API 저장소(maven.fabricmc.net)의 maven-metadata.xml로 확인
→ "26.2"에 태그된 가장 최신 빌드: 0.156.0+26.2

# FabricMC/fabric-example-mod 저장소의 기본 브랜치(26.2)에서 그대로 가져온 실제 빌드 파일:
#   - gradle/wrapper/gradle-wrapper.properties → distributionUrl에 gradle-9.5.1-bin.zip
#   - gradle.properties → loom_version=1.17-SNAPSHOT, loader_version=0.19.3, fabric_api_version=0.156.0+26.2
#   - build.gradle → plugin id 'net.fabricmc.fabric-loom', dependencies { minecraft "com.mojang:minecraft:${...}" ... }
#     (mappings 블록이 아예 없음 — 26.2는 이미 Mojang 공식 이름 그대로라 별도 매핑 아티팩트가 필요 없다)
#   - fabric.mod.json → depends: {"fabricloader": ">=0.19.3", "minecraft": "~26.2", "java": ">=25", "fabric-api": "*"}

# Adoptium API로 확인한 JDK 25 Windows x64 GA 빌드
$ curl "https://api.adoptium.net/v3/assets/feature_releases/25/ga?image_type=jdk&os=windows&architecture=x64&jvm_impl=hotspot"
→ release: jdk-25.0.4+7
→ 다운로드: https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4_7.zip
→ sha256: 7caab7db43bf4b94a2e6252c699e70d90084f9aa7c943cd3414761fd540937ae
```

**주의**: `loom_version=1.17-SNAPSHOT`은 SNAPSHOT 빌드라 시간이 지나면 내용이 바뀔 수 있다 — 하지만 이게 Fabric이 공식 예제에서 지금 실제로 쓰는 값이라 그대로 따른다. Task 1에서 빌드가 안 되면 가장 먼저 의심할 지점.

---

### Task 1: `mod26/` 프로젝트 스캐폴드 + 빌드 성공

**Files:**
- Create: `C:/Users/Skdji/devtools/jdk25/`(JDK 25 압축 해제 위치, 저장소 밖)
- Create: `mod26/settings.gradle`
- Create: `mod26/gradle.properties`
- Create: `mod26/build.gradle`
- Create: `mod26/gradle/wrapper/gradle-wrapper.properties`
- Create: `mod26/gradlew.bat`, `mod26/gradlew`, `mod26/gradle/wrapper/gradle-wrapper.jar`(Gradle wrapper 생성 명령으로 만듦, 아래 Step 참고)
- Create: `mod26/src/main/resources/fabric.mod.json`
- Create: `mod26/src/main/java/com/cubeclient/mod/CubeClientModClient.java`

**Interfaces:**
- Consumes: 없음(새 독립 프로젝트, 기존 `mod/`의 어떤 클래스도 참조하지 않는다).
- Produces: `CubeClientModClient implements ClientModInitializer` — `onInitializeClient()`에서 로그 한 줄만 남긴다. 이후 서브프로젝트(M1)가 이 클래스에 기능 등록 코드를 이어 붙인다.

- [ ] **Step 1: JDK 25 설치**

Run (`mod/`나 다른 프로젝트 디렉터리와 무관하게, 아무 디렉터리에서):
```bash
mkdir -p "C:/Users/Skdji/devtools/jdk25"
curl -sSL -o "C:/Users/Skdji/devtools/jdk25/temurin25.zip" \
  "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4%2B7/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4_7.zip"
```

체크섬 확인:
```bash
sha256sum "C:/Users/Skdji/devtools/jdk25/temurin25.zip"
```
Expected: `7caab7db43bf4b94a2e6252c699e70d90084f9aa7c943cd3414761fd540937ae`와 정확히 일치. 안 맞으면 파일을 지우고 다시 받는다 — 압축 풀지 않는다.

압축 해제 (Windows PowerShell 또는 아무 zip 도구):
```bash
cd "C:/Users/Skdji/devtools/jdk25" && unzip -q temurin25.zip && rm temurin25.zip
```
Expected: `C:/Users/Skdji/devtools/jdk25/jdk-25.0.4+7/` 디렉터리가 생긴다(`jdk21`이 `jdk21/jdk-21.0.11+10/`인 것과 같은 중첩 구조 — 부모 디렉터리가 아니라 이 안쪽 경로가 `JAVA_HOME`이다).

확인:
```bash
"C:/Users/Skdji/devtools/jdk25/jdk-25.0.4+7/bin/java.exe" -version
```
Expected: `openjdk version "25...`로 시작하는 출력.

- [ ] **Step 2: `mod26/settings.gradle` 작성**

`mod/settings.gradle`과 같은 구조(foojay 리졸버는 `mod/`에서도 실제로 검증된 적 없는 안전망이지만 비용이 없으므로 그대로 포함):

```groovy
// mod26/settings.gradle
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // mod/settings.gradle과 같은 이유로 포함 — JDK 25 툴체인을 로컬에서 못 찾을 때의 안전망.
    // 이 프로젝트에서도 실제로 이 경로가 쓰인 적은 없다(JAVA_HOME으로 직접 지정해서 빌드).
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'cubeclient-mod26'
```

- [ ] **Step 3: `mod26/gradle.properties` 작성**

```properties
# mod26/gradle.properties
# 아래 값들은 2026-08-10에 실제로 조회해서 확인한 것 — 계획 문서의 "실제로 확인한 값" 섹션 참고.
# 마인크래프트 26.1은 이미 26.1.1/26.1.2로 패치됐고 안정판 최신은 26.2라 26.2를 대상으로 한다.
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.156.0+26.2

mod_version=0.1.0
maven_group=com.cubeclient.mod
archives_base_name=cubeclient-mod26

org.gradle.jvmargs=-Xmx1G
```

- [ ] **Step 4: `mod26/build.gradle` 작성**

`mod/build.gradle`과 비교했을 때 달라지는 지점: 매핑 블록이 없음(26.2는 이미 Mojang 공식 이름이라 별도 매핑 아티팩트가 필요 없다 — `dependencies` 블록에 `mappings ...` 줄 자체가 없다), `modImplementation`이 아니라 표준 `implementation`(리매핑 스텝이 없어져서), Java 25 툴체인.

```groovy
// mod26/build.gradle
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    // mod/build.gradle과 같은 버전 — 지금은 쓸 테스트가 없지만(기능 0개), M1이 바로 TDD를
    // 시작할 수 있도록 스캐폴딩만 갖춰둔다(스펙의 "테스트" 절 요구사항).
    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}

java {
    // 마인크래프트 26.2 자체가 Java 25를 요구한다(Fabric 공식 문서로 확인, mod/의 Java 21
    // 요구사항과 같은 성격 — 게임 자체 요구사항이지 취향이 아니다).
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property "version", project.version
    filteringCharset "UTF-8"
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}
```

- [ ] **Step 5: Gradle wrapper 생성**

`mod/`에 이미 있는 Gradle 실행 파일로 wrapper를 만든다(직접 Gradle을 설치할 필요 없음):

```bash
cd "C:/Users/Skdji/OneDrive/Desktop/Projects/CubeClient/mod26"
JAVA_HOME="C:/Users/Skdji/devtools/jdk21/jdk-21.0.11+10" "../mod/gradlew.bat" wrapper --gradle-version 9.5.1
```
Expected: `mod26/gradlew.bat`, `mod26/gradlew`, `mod26/gradle/wrapper/gradle-wrapper.jar`, `mod26/gradle/wrapper/gradle-wrapper.properties`가 생긴다. `gradle-wrapper.properties`의 `distributionUrl`이 `gradle-9.5.1-bin.zip`을 가리키는지 확인.

- [ ] **Step 6: `fabric.mod.json` 작성**

```json
{
  "schemaVersion": 1,
  "id": "cubeclient-mod",
  "version": "${version}",
  "name": "CubeClient Mod",
  "description": "The bundled HUD and quality-of-life modpack for the CubeClient launcher.",
  "authors": ["CubeClient"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["com.cubeclient.mod.CubeClientModClient"]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

`mod/`의 `fabric.mod.json`과 다른 점: `main` 엔트리포인트를 아예 선언하지 않는다(`mod/`의 `CubeClientMod`는 지금도 빈 클래스라 이번엔 만들지 않음 — 필요해지면 그때 추가), `mixins` 키 없음(기능 0개라 믹신도 0개).

- [ ] **Step 7: 엔트리포인트 클래스 작성**

```java
// mod26/src/main/java/com/cubeclient/mod/CubeClientModClient.java
package com.cubeclient.mod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 마인크래프트 26.2용 새 툴체인(Java 25, Loom 1.17, Mojang 공식 이름) 뼈대. 아직 기능은
 * 하나도 없다 — 이 클래스의 유일한 책임은 로드됐다는 걸 로그로 증명하는 것.
 */
public class CubeClientModClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cubeclient-mod26");

    @Override
    public void onInitializeClient() {
        LOGGER.info("CubeClient (26.2) initialized");
    }
}
```

- [ ] **Step 8: 빌드 확인**

Run (`mod26/` 디렉터리에서):
```bash
cd "C:/Users/Skdji/OneDrive/Desktop/Projects/CubeClient/mod26"
JAVA_HOME="C:/Users/Skdji/devtools/jdk25/jdk-25.0.4+7" ./gradlew.bat build
```
Expected: `BUILD SUCCESSFUL`. `mod26/build/libs/cubeclient-mod26-0.1.0.jar`가 생긴다.

실패하면(가능성이 큰 원인 순서): (1) `loom_version=1.17-SNAPSHOT`이 그 사이 저장소에서 사라졌을 수 있음 — `https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml`로 최신 버전 재확인. (2) `fabric_api_version=0.156.0+26.2`가 사라졌을 수 있음 — `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`로 재확인. 추측으로 아무 값이나 넣지 말고 반드시 실제 API 응답으로 교체한다.

- [ ] **Step 9: 커밋**

```bash
git add mod26/
git commit -m "Add mod26: zero-feature Fabric skeleton for Minecraft 26.2's Java-25/Loom-1.17/Mojang-mappings toolchain"
```

---

### Task 2: 실기기 검증

**Files:** 없음(수동 검증).

**Interfaces:**
- Consumes: Task 1의 `mod26/build/libs/cubeclient-mod26-0.1.0.jar`.
- Produces: 없음 — 문제가 있으면 Task 1로 돌아가 고친다.

이번엔 런처가 아직 26.2용 로더/모드 jar를 자동으로 골라주지 못한다(스펙의 "범위 밖" 항목, L1에서 다룸) — 손으로 인스턴스를 만든다.

- [ ] **Step 1: 26.2 Fabric 인스턴스를 손으로 준비**

기존 인스턴스 폴더 구조(`%APPDATA%\CubeClient\instances\fabric-1.21.4\`)를 참고해 `%APPDATA%\CubeClient\instances\fabric-26.2\` 아래에:

1. `mods/` 폴더를 만든다.
2. Fabric API 26.2 빌드를 받아 `mods/`에 넣는다:
   ```bash
   curl -sSL -o "$APPDATA/CubeClient/instances/fabric-26.2/mods/fabric-api-0.156.0+26.2.jar" \
     "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.156.0+26.2/fabric-api-0.156.0+26.2.jar"
   ```
3. Task 1에서 만든 `mod26/build/libs/cubeclient-mod26-0.1.0.jar`도 같은 `mods/`에 복사한다.
4. 마인크래프트 26.2 본체 + Fabric Loader 0.19.3 설치는 큐브클라이언트 런처로 새 프로필(버전 `26.2`, 로더 `fabric`)을 만들어서 진행한다 — 런처의 기존 버전 매니페스트/로더 설치 경로가 코드 변경 없이 26.2를 받아오는지 자체가 이번 검증의 일부다.

- [ ] **Step 2: 실행 및 확인**

큐브클라이언트 런처로 방금 만든 프로필을 실행한다.

확인 항목:
- 게임이 크래시 없이 타이틀 화면까지 뜨는지.
- 인스턴스의 `logs/latest.log`에 `"CubeClient (26.2) initialized"`가 찍혀 있는지.
- (부수적으로) 런처가 여기까지 오는 동안 버전 다운로드·JRE 자동설치(Java 25가 필요하다는 걸 매니페스트에서 읽어 자동으로 받아왔는지)·로더 설치 중 어디선가 실패했다면, 그 실패 지점을 정확히 기록해둔다 — 다음 서브프로젝트(L1)의 실제 스코프를 정하는 근거가 된다.

- [ ] **Step 3: 문제 발견 시 Task 1로 돌아가 수정**

빌드는 됐는데 게임에서 크래시하거나 로그가 안 찍히면, 크래시 리포트/로그를 근거로 원인을 규명하고 Task 1의 해당 파일을 고친 뒤 다시 빌드·재배포·재확인한다. 추측성 수정 금지 — 이 프로젝트의 기존 원칙 그대로.

- [ ] **Step 4: 최종 커밋(문제를 고쳤을 경우만)**

```bash
git add mod26/
git commit -m "Fix issues found during mod26 skeleton real-device verification"
```
