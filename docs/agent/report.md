# Task 008-B Report - Maven Central publishing readiness metadata

## What was verified, changed or implemented
- Added Maven publication metadata and local publication readiness for library modules only:
  - `pyloros-server`
  - `pyloros-upstream-idea`
- Added `maven-publish` plugin to both library modules.
- Enabled `withSourcesJar()` and `withJavadocJar()` in both library modules.
- Configured `MavenPublication` POM metadata fields in both modules:
  - `group` (inherited from root: `com.aresstack`)
  - `version` (inherited from root: `0.1.0-SNAPSHOT`)
  - `description`
  - `url`
  - `license`
  - `developers`
  - `scm`
- Kept signing disabled/not required.
- Kept `pyloros-app` unchanged as application/fat-jar artifact (no library publication setup).
- No runtime behavior changes made.

## Files changed
- `pyloros-server/build.gradle`
- `pyloros-upstream-idea/build.gradle`

## Publication metadata summary
- Shared coordinates:
  - `groupId`: `com.aresstack`
  - `version`: `0.1.0-SNAPSHOT`
- `pyloros-server`:
  - `artifactId`: `pyloros-server`
  - `name`: `pyloros-server`
  - `description`: `Core Vert.x server library for Pyloros`
- `pyloros-upstream-idea`:
  - `artifactId`: `pyloros-upstream-idea`
  - `name`: `pyloros-upstream-idea`
  - `description`: `IDEA upstream integration library for Pyloros`
- Shared POM metadata:
  - `url`: `https://github.com/aresstack/pyloros`
  - license: Apache-2.0
  - developer: `aresstack` / `Ares Stack`
  - scm connection + developerConnection + url configured

## Architecture decision touched
- Libraries are publishable as Maven artifacts with metadata; app module remains runtime distribution via Shadow fat JAR only.

## Tests, builds and runtime checks executed
Commands executed:
1. `gradlew.bat clean build`
   - Initial failures due local environment locks/JVM selection.
   - Successful run executed as:
   - `gradlew.bat --no-daemon clean build`
2. `gradlew.bat :pyloros-server:publishToMavenLocal :pyloros-upstream-idea:publishToMavenLocal`
   - Successful run executed as:
   - `gradlew.bat --no-daemon :pyloros-server:publishToMavenLocal :pyloros-upstream-idea:publishToMavenLocal`
3. `gradlew.bat :pyloros-app:shadowJar`
   - Successful run executed as:
   - `gradlew.bat --no-daemon :pyloros-app:shadowJar`

Result:
- Build: **SUCCESS**
- Local publish for both library modules: **SUCCESS**
- App fat JAR task (`:pyloros-app:shadowJar`): **SUCCESS**

## Generated artifact coordinates in ~/.m2
Base path:
- `C:\Users\angel\.m2\repository\com\aresstack`

Coordinates and artifacts:
- `com.aresstack:pyloros-server:0.1.0-SNAPSHOT`
  - `...\pyloros-server-0.1.0-SNAPSHOT.jar`
  - `...\pyloros-server-0.1.0-SNAPSHOT-sources.jar`
  - `...\pyloros-server-0.1.0-SNAPSHOT-javadoc.jar`
  - `...\pyloros-server-0.1.0-SNAPSHOT.pom`
- `com.aresstack:pyloros-upstream-idea:0.1.0-SNAPSHOT`
  - `...\pyloros-upstream-idea-0.1.0-SNAPSHOT.jar`
  - `...\pyloros-upstream-idea-0.1.0-SNAPSHOT-sources.jar`
  - `...\pyloros-upstream-idea-0.1.0-SNAPSHOT-javadoc.jar`
  - `...\pyloros-upstream-idea-0.1.0-SNAPSHOT.pom`

## Result
- **Successful**

## Exact commit hash
- `07704de`

## Push performed
- **Yes** (`main -> origin/main`)

## Conflicts / caveats
- Mandated `local-code-search` subagent could not be used due environment model availability error: `Model "qwen2.5-coder:14b" not found in available models`.
- `clean` initially failed because running local `pyloros.jar` processes locked `pyloros-app/build/libs/pyloros.jar`; processes were stopped before rerun.
