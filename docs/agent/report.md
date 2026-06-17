# Main-Push Runnable Jar Workflow — Report

## What was verified, changed or implemented?

- Restored `.github/workflows/release.yml` as the Maven Central release workflow for `v*` tag pushes and manual dispatch.
- Added `.github/workflows/build-jar.yml` as a separate workflow for `main` branch pushes.
- The new main-push workflow builds the runnable Pyloros Shadow jar with `:pyloros-app:shadowJar`.
- The new workflow verifies before uploading:
  - `com/aresstack/pyloros/PylorosApplication.class` exists.
  - `META-INF/MANIFEST.MF` contains `Main-Class: com.aresstack.pyloros.PylorosApplication`.
  - Dependency classes from Vert.x and Jackson exist inside the jar.
- The runnable jar is uploaded as a GitHub Actions artifact named `pyloros.jar`.
- The runnable jar is also uploaded to a moving GitHub pre-release named `Latest main build` on tag `main-latest`.

## Which files were changed or newly created?

- Restored/changed: `.github/workflows/release.yml`
- New/changed: `.github/workflows/build-jar.yml`
- Changed: `docs/agent/report.md`

## Which architecture decision was touched?

- Maven Central publishing remains tag-based in `release.yml`.
- Runnable application packaging is separated into a main-push workflow that publishes `pyloros.jar` to the `main-latest` GitHub pre-release.
- The application entry point remains `com.aresstack.pyloros.PylorosApplication`.
- Runtime dependencies are bundled through the existing Shadow plugin configuration in `pyloros-app`.

## Which tests, builds and runtime checks were executed?

- `.\gradlew.bat "-Pversion=0.1.0" --no-daemon :pyloros-app:shadowJar`
  - Result: successful.
- Jar content check:
  - Verified `com/aresstack/pyloros/PylorosApplication.class`.
  - Verified `io/vertx/core/Vertx.class`.
  - Verified `com/fasterxml/jackson/databind/ObjectMapper.class`.
- Manifest check:
  - Verified `Main-Class: com.aresstack.pyloros.PylorosApplication`.
- Attempted exact clean build:
  - `.\gradlew.bat "-Pversion=0.1.0" --no-daemon clean :pyloros-app:shadowJar`
  - Result: failed locally because Windows has `pyloros-app\build\libs\pyloros.jar` locked by another process.
  - The GitHub Action runs on a fresh Ubuntu runner, so this local Windows file lock should not apply there.

## Result: successful

The requested separation is implemented: `v*` tag pushes keep Maven Central release behavior, and main pushes now build `pyloros.jar` and upload it to GitHub Releases under `main-latest`.

## If failed: exact error and recommended next step

Local clean error:

```text
Execution failed for task ':pyloros-app:clean'.
java.io.IOException: Unable to delete directory 'C:\Projects\pyloros\pyloros-app\build'
Failed to delete some children.
- C:\Projects\pyloros\pyloros-app\build\libs\pyloros.jar
- C:\Projects\pyloros\pyloros-app\build\libs
```

Recommended next step if local `clean` must be rerun: close the process that holds `pyloros-app\build\libs\pyloros.jar`, then rerun:

```powershell
.\gradlew.bat "-Pversion=0.1.0" --no-daemon clean :pyloros-app:shadowJar
```

## Exact commit hash, or No commit created

No commit created
