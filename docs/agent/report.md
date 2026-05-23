# Task 008-A Report - Split Gradle modules for Maven Central readiness

## What was verified, changed or implemented
- Converted the single-module build into a Gradle multi-project with:
  - `pyloros-server`
  - `pyloros-upstream-idea`
  - `pyloros-app`
- Preserved runtime behavior and wiring:
  - OAuth logic remains in `OAuthService`.
  - IDEA forwarding classes remain unchanged functionally.
  - `PylorosApplication` remains bootstrap/wiring only.
- Added server-side config contract `ServerConfig` to avoid `pyloros-server -> pyloros-app` dependency.
- Added `HealthRoutes` class in server module and mounted it from app.
- Kept Shadow fat JAR generation only in `pyloros-app` with stable name `pyloros.jar`.
- Moved `application.properties` to `pyloros-app/src/main/resources/application.properties`.
- Moved tests to the proper module (`pyloros-server`).

## Final module layout
- `pyloros-server`: reusable library (HTTP/OAuth/domain/tool registry logic)
- `pyloros-upstream-idea`: IDEA MCP adapter (`dependsOn :pyloros-server`)
- `pyloros-app`: runnable app module (`dependsOn :pyloros-server, :pyloros-upstream-idea`, Shadow jar)

## Files changed or newly created
- Updated:
  - `settings.gradle`
  - `build.gradle` (root)
  - `README.md`
  - Java sources moved into module directories
- Created:
  - `pyloros-server/build.gradle`
  - `pyloros-upstream-idea/build.gradle`
  - `pyloros-app/build.gradle`
  - `pyloros-server/src/main/java/com/aresstack/pyloros/config/ServerConfig.java`
  - `pyloros-server/src/main/java/com/aresstack/pyloros/http/HealthRoutes.java`
- Moved/adjusted:
  - app bootstrap/config to `pyloros-app`
  - generic HTTP/OAuth/domain/tool code to `pyloros-server`
  - IDEA upstream code to `pyloros-upstream-idea`
  - OAuth replay test to `pyloros-server`

## Architecture decision touched
- Separated reusable server library concerns from runtime application packaging to prepare for Maven Central readiness, while keeping only app-module fat JAR packaging.

## Tests, builds and runtime checks executed
1. `gradlew.bat clean build` -> **SUCCESS**
2. `gradlew.bat :pyloros-app:shadowJar` -> **SUCCESS**
3. Runtime command tested:
   - `java -jar .\\pyloros-app\\build\\libs\\pyloros.jar` (failed with system default Java 8)
   - `& "C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe" -jar .\\pyloros-app\\build\\libs\\pyloros.jar` -> started
4. Health check:
   - `GET http://127.0.0.1:8081/health` -> `200 {"status":"ok"}`

## Shadow JAR result
- Exact path: `C:\\Projects\\pyloros\\pyloros-app\\build\\libs\\pyloros.jar`

## Public connector / intellij/get_project_modules verification
- Not verifiable in this local run (requires reachable IntelliJ MCP upstream session + valid bearer token).
- Next-step command:
  - `Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8081/sse' -Headers @{ Authorization = 'Bearer <ACCESS_TOKEN>' } -ContentType 'application/json' -Body '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"intellij/get_project_modules","arguments":{"projectPath":"C:\\\\Projects\\\\pyloros"}}}'`

## Result
- **Successful**

## Exact commit hash
- `36d1d08`

## Push performed
- **Yes** (`main -> origin/main`)

## Notes
- Attempt to use the mandated `local-code-search` subagent failed in this environment due unavailable model (`qwen2.5-coder:14b` not found); implementation proceeded with direct workspace inspection tools.
