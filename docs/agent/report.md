# Task Report: 009-E - Replace fragile ToolProvider routing with map-backed ToolCatalog routing

## What was verified, changed or implemented?

Implemented stable, map-backed routing for MCP tool names so canonical slash names are treated as exact MCP tool names instead of path-like strings.

Implemented changes:
- Replaced fragile ToolProvider routing assumptions with exact-name catalog lookup.
- Introduced immutable `ToolCatalogSnapshot` with:
  - `toolsByExternalName`
  - `toolsByAddress`
  - `toolsByProviderId`
- Stabilized `ToolAddress` as a value object with:
  - `providerId`
  - `upstreamToolName`
- Simplified `ToolCatalogEntry` to hold:
  - `externalName`
  - `address`
  - immutable original descriptor data
- Refactored `ToolCatalog` to build external names once during refresh and replace snapshots atomically.
- Refactored `ToolRouter` to route only by exact `request.params.name` lookup in the catalog snapshot.
- Removed router fallback behavior based on prefix scanning / `startsWith()` / path-like thinking.
- Refactored `ToolProvider` contract so providers only deal with upstream tool names and arguments.
- Updated providers (`pyloros`, `intellij`, `github`, `intellij-index`, generic MCP upstream provider) to the new contract.
- Updated smoke test so it verifies local Pyloros routing with exact names:
  - `pyloros__ping`
  - `intellij/get_project_modules`
  - `github/get_me`
  - `intellij-index/ide_index_status`
- Updated `README.md` to document:
  - Pyloros as headless capability gateway
  - transport matrix
  - canonical slash naming
  - startup env vars for upstreams
  - connector refresh after restart
  - difference between local smoke test and real ChatGPT / `api_tool` invocation

## Which files were changed or newly created?

Changed:
- `README.md`
- `docs/agent/assignment.md`
- `docs/agent/report.md`
- `pyloros-app/src/main/java/com/aresstack/pyloros/smoke/McpAggregationSmokeTest.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/provider/ProviderRegistry.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/PylorosPingToolProvider.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolAddress.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalog.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalogEntry.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolProvider.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolRouter.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalogSnapshot.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/GenericMcpToolProvider.java`
- `pyloros-upstream-github/src/main/java/com/aresstack/pyloros/upstream/github/GitHubToolProvider.java`
- `pyloros-upstream-idea/src/main/java/com/aresstack/pyloros/upstream/idea/IdeaMcpClient.java`
- `pyloros-upstream-idea/src/main/java/com/aresstack/pyloros/upstream/idea/IdeaToolProvider.java`
- `pyloros-upstream-intellij-index/src/main/java/com/aresstack/pyloros/upstream/intellijindex/IntellijIndexToolProvider.java`

New:
- `pyloros-server/src/test/java/com/aresstack/pyloros/tool/ToolCatalogRoutingTest.java`

## Which architecture decision was touched?

Touched decisions:
- canonical external MCP tool names remain `provider/tool`
- slash is intentionally preserved as part of the tool name
- routing is map-based through immutable catalog snapshots
- providers operate only on upstream tool names
- duplicate provider ids and duplicate external tool names fail fast
- native tools such as `pyloros__ping` remain supported without path parsing logic

## Which tests, builds and runtime checks were executed?

### 1) Targeted routing tests

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :pyloros-server:test --tests com.aresstack.pyloros.tool.ToolCatalogRoutingTest --no-daemon
```

Result: `BUILD SUCCESSFUL`

Covered tests:
- `listToolsBuildsStableSnapshotWithSlashNames()`
- `duplicateProviderIdFailsFast()`
- `routerRoutesIntellijSlashNameToProviderAndUpstreamTool()`
- `routerRoutesGithubSlashNameToProviderAndUpstreamTool()`
- `unknownToolNameReturnsCleanErrorResult()`
- `duplicateExternalNameFailsFast()`
- `routerRoutesHyphenatedProviderIdToProviderAndUpstreamTool()`

### 2) Full build

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean build --no-daemon
```

Result: `BUILD SUCCESSFUL`

### 3) Fat jar build

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :pyloros-app:shadowJar --no-daemon
```

Result: `BUILD SUCCESSFUL`

### 4) Runtime verification with all upstreams enabled

Executed local runtime verification against a running fat jar using:
- IntelliJ SSE upstream
- GitHub Streamable HTTP upstream
- intellij-index Streamable HTTP upstream
- fixed local bearer token for Pyloros

Verified over local MCP endpoint:
- `tools/list` contained exact canonical names:
  - `intellij/get_project_modules`
  - `github/get_me`
  - `intellij-index/ide_index_status`
- `tools/call` succeeded with exact slash names:
  - `intellij/get_project_modules` -> `isError=false`
  - `github/get_me` -> `isError=false`
  - `intellij-index/ide_index_status` -> `isError=false`

### 5) Updated smoke test

Executed:

```powershell
$env:PYLOROS_SMOKE_ACCESS_TOKEN='smoke-009e-token'
$env:PYLOROS_SMOKE_MCP_URL='http://127.0.0.1:8095/sse'
.\gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

Observed result:
- `pyloros__ping` found and call succeeded
- `intellij/get_project_modules` found and call succeeded
- `github/get_me` found and call succeeded
- `intellij-index/ide_index_status` found and call succeeded
- Summary:
  - `Total tools: 83`
  - `Tests passed: 8`
  - `Tests failed: 0`
  - `Result: SUCCESS`

### 6) Acceptance verification summary

- `gradlew.bat clean build` green: ✅
- fat jar builds: ✅
- Pyloros starts with intellij + github + intellij-index: ✅
- `tools/list` contains slash names: ✅
- local `tools/call` works with exact slash names: ✅
- `README.md` updated: ✅
- `docs/agent/report.md` updated: ✅

## Result: successful or failed

Successful.

## If failed: exact error and recommended next step

Not failed.

## Exact commit hash, or No commit created

No commit created.
