# Task 007 Report - Fat Shadow JAR Runtime Artifact

## What was verified, changed, or implemented
- Configured Gradle Groovy build to produce a runnable fat JAR using Shadow.
- Set the runnable manifest `Main-Class` to `com.aresstack.pyloros.PylorosApplication`.
- Kept Java 21 toolchain unchanged.
- Kept `application.properties` defaults, env support behavior, OAuth semantics, and IDEA forwarding code unchanged.
- Updated README to document fat JAR build/start as the runtime target.

## Files changed
- `build.gradle`
- `README.md`
- `docs/agent/report.md`

## Architecture decision touched
- Runtime packaging target is a single runnable fat Shadow JAR (`java -jar`) instead of operator-script/distribution runtime targets.

## Shadow plugin version used
- `com.gradleup.shadow` version `9.0.0`

## Build / test / runtime checks executed
1. `.\gradlew.bat clean build` -> **SUCCESS**
2. `.\gradlew.bat clean shadowJar` -> **SUCCESS**
3. Fat jar produced at exact path: `C:\Projects\pyloros\build\libs\pyloros.jar`
4. Exact runtime command tested: `java -jar .\build\libs\pyloros.jar`
5. Healthcheck command/result:
   - Request: `Invoke-WebRequest -Uri 'http://127.0.0.1:8081/health' -UseBasicParsing -TimeoutSec 5`
   - Result: `STATUS=200 BODY={"status":"ok"}`

## Connector verification status
- Public connector full functional verification with authenticated upstream call: **NOT fully verified locally**.
- `intellij/get_project_modules` end-to-end verification against running IntelliJ MCP upstream: **NOT verified locally**.
- Reason: local run did not include a validated OAuth token flow plus confirmed reachable IntelliJ MCP upstream instance.
- Exact next-step command (after obtaining valid bearer token and ensuring IntelliJ MCP is reachable):
  - SSE reachability:
    - `Invoke-WebRequest -Uri 'http://127.0.0.1:8081/sse' -Headers @{ Authorization = 'Bearer <ACCESS_TOKEN>' } -UseBasicParsing`
  - IntelliJ tool call:
    - `Invoke-WebRequest -Uri 'http://127.0.0.1:8081/sse' -Method Post -ContentType 'application/json' -Headers @{ Authorization = 'Bearer <ACCESS_TOKEN>' } -Body '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"idea__get_project_modules","arguments":{}}}' -UseBasicParsing`

## Result
- **Successful** for Task 007 scope (fat runnable Shadow JAR + documented commands + healthcheck).

## Exact commit hash
- Pending commit at report-write time.

## Push performed
- Pending at report-write time.
