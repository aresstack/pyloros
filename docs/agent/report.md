# Report: R6-08 end-to-end smoke test for ACP manager agent with injected MCP tools

## What was verified, changed or implemented?
- Added an automated smoke test for the minimal manager-agent R6 flow that uses a real JSON-RPC protocol server plus an injected fake MCP HTTP endpoint.
- The new test verifies:
  - manager agent receives injected MCP server config from `session/new`,
  - manager agent executes `tools/list`,
  - manager agent executes safe `tools/call` (`pyloros__ping`),
  - manager agent emits structured ACP responses via `session/update` text and completion events.
- Updated the R6 smoke-test documentation with:
  - the new automated test entry and command,
  - explicit error-path documentation for missing agent process, missing MCP endpoint, and forbidden/recursive agent-view setup.

## Which files were changed or newly created?
- New: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/test/java/com/aresstack/pyloros/manageragent/ManagerAgentInjectedMcpSmokeTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/docs/smoke-test/r6-manager-agent-smoke-test.md`

## Which architecture decision was touched?
- Strengthened the existing R6 separation between Pyloros ACP infrastructure and standalone manager-agent runtime by validating injected MCP tool usage end-to-end without introducing workflow-manager or multi-agent orchestration behavior.

## Which tests, builds and runtime checks were executed?
- Baseline before changes:
  - `./gradlew --no-daemon :pyloros-manager-agent:test :pyloros-server:test` → SUCCESS
- Targeted checks during implementation:
  - `./gradlew --no-daemon :pyloros-manager-agent:test --tests "com.aresstack.pyloros.manageragent.ManagerAgentInjectedMcpSmokeTest" --tests "com.aresstack.pyloros.manageragent.ManagerAgentHandshakeHandlerTest"` → SUCCESS
- Post-change module check:
  - `./gradlew --no-daemon :pyloros-manager-agent:test` → SUCCESS
- Final validation:
  - `parallel_validation` run twice:
    - CodeQL Security Scan → SUCCESS (skipped as trivial test/doc-only changes)
    - Code Review → FAILED due external HTTP 400 header issue

## Result: successful or failed
- Successful (required smoke coverage and documentation delivered; code/test verification passed).

## If failed: exact error and recommended next step
- External validation service failure (non-code):
  - `HTTP error 400: bad request: Unexpected value(s) 'context-1m-2025-08-07' for the 'anthropic-beta' header`
- Recommended next step:
  - Re-run `parallel_validation` after the external Code Review service/header issue is resolved.

## Exact commit hash, or No commit created
- `aa51aa9`
