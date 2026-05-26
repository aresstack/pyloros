# Report: R6-06 inject Pyloros MCP aggregator into manager agent session

## What was verified, changed or implemented?

- Implemented ACP session MCP injection for the manager-agent flow:
  - ACP client now sends `session/new` with `cwd` plus `mcpServers`.
  - ACP virtual provider now derives injected MCP server config from ACP process environment (`PYLOROS_MCP_URL`, optional `PYLOROS_MCP_BEARER_TOKEN`) and binds `agentToolView` via `?view=<agentToolView>`.
- Implemented manager-agent MCP consumption path:
  - `session/new` now validates and stores injected MCP endpoint config from `mcpServers.pyloros`.
  - `session/prompt` now executes a minimal smoke flow: MCP `tools/list` then safe MCP `tools/call`, then emits ACP response/session updates.
  - Missing/invalid injected MCP endpoint and MCP invocation failures are reported as structured ACP errors.
- Implemented view-scoped MCP routing for server endpoint calls:
  - MCP HTTP route now supports `view` query selection (`ToolView.named(...)`).
  - `ToolRouter` supports dispatch scoped to a specific `ToolView`, used by routes for both `tools/list` and `tools/call`.
- Added tests covering successful and error paths, including:
  - manager-agent success path (`tools/list` + safe `tools/call`) and missing-endpoint failure.
  - ACP runtime propagation of `mcpServers` into `session/new`.
  - tool-router view scoping to ensure hidden tools are not callable in excluded views.

## Which files were changed or newly created?

- Changed: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentHandshakeHandler.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentSessionState.java`
- New: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/main/java/com/aresstack/pyloros/manageragent/ManagerAgentMcpGateway.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/test/java/com/aresstack/pyloros/manageragent/ManagerAgentHandshakeHandlerTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-manager-agent/src/test/java/com/aresstack/pyloros/manageragent/ManagerAgentProtocolServerTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpAgentClient.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpVirtualToolProvider.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolRouter.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/acp/AcpVirtualToolProviderTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/acp/FakeAcpAgent.java`
- New: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/tool/ToolRouterViewScopingTest.java`

## Which architecture decision was touched?

- R6 manager-agent MCP injection decision: the ACP manager-agent receives MCP aggregator configuration via `session/new` (`mcpServers`) and uses this injected endpoint for MCP `tools/list`/`tools/call`.
- `agentToolView` scoping is preserved through injected endpoint view binding and view-scoped MCP route dispatch.
- Recursion-protection constraints remain active (no change to `AgentToolViewValidator` behavior).

## Which tests, builds and runtime checks were executed?

- Baseline before changes:
  - `./gradlew --no-daemon :pyloros-manager-agent:compileJava :pyloros-manager-agent:test` → SUCCESS
- After manager-agent + server changes:
  - `./gradlew --no-daemon :pyloros-manager-agent:compileJava :pyloros-manager-agent:test` → SUCCESS
  - `./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:test --tests "com.aresstack.pyloros.acp.AcpVirtualToolProviderTest" --tests "com.aresstack.pyloros.acp.AcpIntegrationTest"` → SUCCESS
  - `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.tool.ToolRouterViewScopingTest" --tests "com.aresstack.pyloros.tool.ToolCatalogViewFilteringSemanticsTest"` → SUCCESS
- Parallel validation:
  - `parallel_validation` (run twice)
  - CodeQL: SUCCESS, 0 alerts
  - Code Review: FAILED due external backend/header issue (HTTP 400, anthropic-beta header)

## Result: successful or failed

Successful (requested implementation and tests are complete; only external Code Review service/tooling failure remains).

## If failed: exact error and recommended next step

- Non-blocking external tool failure from `parallel_validation` Code Review:
  - `HTTP error 400: bad request: Unexpected value(s) 'context-1m-2025-08-07' for the 'anthropic-beta' header`
- Recommended next step:
  - Re-run `parallel_validation` Code Review when the backend/header issue is resolved.

## Exact commit hash, or No commit created

- `46a7f20`
