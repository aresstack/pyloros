# Task Report: 009-D - Generic Streamable HTTP MCP upstream client

## What was verified, changed or implemented?

- Implemented transport abstraction for MCP upstreams:
  - `McpUpstreamClient`
  - `SseMcpUpstreamClient`
  - `StreamableHttpMcpUpstreamClient`
- Implemented generic provider layer:
  - `McpUpstreamConfig`
  - `McpUpstreamClients` (transport-based factory)
  - `GenericMcpToolProvider`
- Refactored GitHub provider to use generic transport/provider path (no GitHub-specific transport logic in provider behavior).
- Added new upstream module/provider for `intellij-index` (streamable-http).
- Updated app wiring/config so transport is provider configuration (`sse` vs `streamable-http`) and not hardcoded provider dispatch logic.
- Verified aggregation now includes: native + IntelliJ SSE + GitHub streamable-http + intellij-index streamable-http.

## Which files were changed or newly created?

Changed:
- `settings.gradle`
- `pyloros-server/build.gradle`
- `pyloros-app/build.gradle`
- `pyloros-app/src/main/java/com/aresstack/pyloros/PylorosApplication.java`
- `pyloros-app/src/main/java/com/aresstack/pyloros/config/PylorosConfig.java`
- `pyloros-app/src/main/resources/application.properties`
- `pyloros-upstream-github/src/main/java/com/aresstack/pyloros/upstream/github/GitHubToolProvider.java`
- `pyloros-app/src/main/java/com/aresstack/pyloros/smoke/McpAggregationSmokeTest.java`
- `docs/agent/report.md`

New:
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/McpUpstreamConfig.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/McpUpstreamClient.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/McpUpstreamClients.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/NoopMcpUpstreamClient.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/GenericMcpToolProvider.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/StreamableHttpMcpUpstreamClient.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/SseMcpUpstreamClient.java`
- `pyloros-upstream-intellij-index/build.gradle`
- `pyloros-upstream-intellij-index/src/main/java/com/aresstack/pyloros/upstream/intellijindex/IntellijIndexToolProvider.java`

## Which architecture decision was touched?

- Introduced explicit transport abstraction (`sse` / `streamable-http`) for MCP upstream communication.
- Kept `PylorosApplication` as wiring/bootstrap only.
- Kept aggregation through `ToolProvider` + `ProviderRegistry` + `ToolCatalog`.
- Added `intellij-index` as separate provider (separate module), preserving optional-upstream startup resilience.

## Which tests, builds and runtime checks were executed?

1) Build

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean build
```

Result: `BUILD SUCCESSFUL`.

2) Runtime verification with GitHub + intellij-index enabled (no token values printed)

```powershell
# Start server with env (token loaded from local mcp.json; not logged)
# Then call tools/list over POST /sse and run smoke test
```

Observed runtime result:
- `tools_total=83`
- `tools_intellij=22`
- `tools_github=45`
- `tools_intellij_index=15`

3) Smoke test against running server

```powershell
.\gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

Result:
- `pyloros__ping` call: PASS
- `intellij/get_project_modules` call: PASS
- `github/get_me` call: PASS
- Summary: `Tests passed: 6`, `Tests failed: 0`, `Result: SUCCESS`

4) Direct intellij-index tools/call check (raw JSON-RPC via Pyloros `/sse`)

Observed result:
- selected tool: `intellij-index/ide_index_status`
- call result: `isError=false`

## Result: successful or failed

- **Successful**

Acceptance status:
- IntelliJ SSE upstream works: ✅
- GitHub Streamable HTTP upstream works: ✅
- intellij-index Streamable HTTP upstream works: ✅
- ToolCatalog aggregates all configured upstreams: ✅
- Transport type is configuration-driven: ✅
- No secrets committed/logged in report: ✅

## If failed: exact error and recommended next step

- Not applicable (successful).

## Exact commit hash, or No commit created

- **No commit created**
