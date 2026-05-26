# Report: R6-05 minimal ACP manager-agent handshake

## What was verified, changed or implemented?

- Implemented a minimal ACP stdio protocol server in `pyloros-manager-agent` that:
  - accepts `session/new`
  - accepts `session/prompt`
  - returns simple `session/update` text + completion events
  - returns structured JSON-RPC errors for invalid requests
- Wired the protocol loop into the manager-agent bootstrap verticle so the module is startable as a process and serves ACP over stdio.
- Added focused tests for handshake success path and structured error responses.
- Updated Release 6 requirement documentation status to reflect minimal handshake support.

## Which files were changed or newly created?

- Changed: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentBootstrapVerticle.java`
- New: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentProtocolServer.java`
- New: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/test/java/com/aresstack/pyloros/manageragent/ManagerAgentProtocolServerTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/docs/requirements/006-acp-manager-agent.md`

## Which architecture decision was touched?

- Release 6 manager-agent scope decision: keep `pyloros-manager-agent` as separate Java 21 bootstrap/application module, implement only minimal ACP handshake now, and keep logs stdio-safe (`stdout` for ACP JSON-RPC, logs via `stderr`).

## Which tests, builds and runtime checks were executed?

- Baseline before changes:
  - `./gradlew --no-daemon :pyloros-manager-agent:compileJava :pyloros-manager-agent:test :pyloros-server:test` (SUCCESS)
- After changes:
  - `./gradlew --no-daemon :pyloros-manager-agent:compileJava :pyloros-manager-agent:test :pyloros-server:test` (SUCCESS)
  - `./gradlew --no-daemon :pyloros-manager-agent:installDist` (SUCCESS)
- Manual runtime smoke checks:
  - Started `pyloros-manager-agent` distribution script with Java 21 and verified:
    - `session/new` response with `sessionId`
    - `session/prompt` response plus `session/update` text/completion events
    - process exits cleanly after stdin close
  - Verified structured error outputs for invalid request input (`Parse error`, invalid params)
- Final validation:
  - `parallel_validation` run twice
  - CodeQL: SUCCESS, 0 alerts
  - Code Review: tool-side HTTP 400 failure (`anthropic-beta` header issue), retried with same result

## Result: successful or failed

Successful (implementation, tests, and runtime smoke checks passed; remaining issue is external Code Review tool failure).

## If failed: exact error and recommended next step

- Non-blocking external tool failure:
  - `Code review tool encountered an issue: HTTP error 400 ... Unexpected value(s) 'context-1m-2025-08-07' for the 'anthropic-beta' header`
- Recommended next step:
  - rerun `parallel_validation` once the review backend/header configuration is fixed; no code change indicated by this error.

## Exact commit hash, or No commit created

- `4cd921f`
