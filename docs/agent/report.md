# Report: R6-03 Java 21 ACP Manager Agent module shape

## What was verified, changed or implemented?

- Verified baseline on current branch with a full green Gradle build before changes.
- Added new Gradle subproject `pyloros-manager-agent` as minimal Java-21-compatible standalone manager-agent module shape.
- Added minimal bootstrap entrypoint (`ManagerAgentApplication`) and bootstrap verticle (`ManagerAgentBootstrapVerticle`) without manager logic, model selection, or A2A integration.
- Pinned manager-agent SDK baseline dependencies per R6-04:
  - `com.agentclientprotocol:acp-core:0.11.0`
  - `io.modelcontextprotocol.sdk:mcp:1.1.3`
- Documented module boundaries and separation from `pyloros-server` and `pyloros-app`.
- Kept core modules independent: no dependency from core modules to `pyloros-manager-agent` was introduced.

## Which files were changed or newly created?

- `settings.gradle` (changed)
- `pyloros-manager-agent/build.gradle` (new)
- `pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentApplication.java` (new)
- `pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentBootstrapVerticle.java` (new)
- `docs/requirements/006-acp-manager-agent.md` (changed)
- `README.md` (changed)
- `docs/agent/report.md` (changed)

## Which architecture decision was touched?

- R6 architecture decision that the ACP Manager Agent remains a separate process/JAR and is not a Pyloros core component.
- R6-04 SDK baseline decision reflected in module shape: ACP SDK `0.11.0`, MCP SDK `1.1.3`.

## Which tests, builds and runtime checks were executed?

- `./gradlew --no-daemon build` → SUCCESS
- `./gradlew --no-daemon :pyloros-manager-agent:build :pyloros-server:test build` → SUCCESS
- `./gradlew --no-daemon :pyloros-manager-agent:build` → SUCCESS
- `parallel_validation`:
  - CodeQL Security Scan → SUCCESS (0 alerts)
  - Code Review → FAILED due to external HTTP 400 tooling/header error

## Result: successful or failed

Successful (code/build/test targets passed; acceptance-related changes implemented)

## If failed: exact error and recommended next step

- Non-blocking tooling failure in `parallel_validation` Code Review:
  - `HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header`
- Recommended next step: re-run `parallel_validation` Code Review after external tool/header issue is resolved.

## Exact commit hash, or No commit created

- `7dc4c6b7fae2e0eddfc0d311a23a8ea24a6ae23e`
