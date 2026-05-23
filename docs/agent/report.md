# Task Report - 009-A

## What was verified, changed or implemented?
- Implemented architecture split in `pyloros-server`:
  - `ProviderRegistry`
  - `ToolCatalog`
  - `ToolCatalogEntry`
  - `ToolAddress`
  - `ToolRouter`
- Kept `ToolProvider` interface compatible by adding default methods (`providerId`, `nativeToolName`) so existing providers still work.
- Updated `McpRoutes` integration:
  - `tools/list` now uses `ToolCatalog`
  - `tools/call` now uses `ToolRouter`
- Updated bootstrap wiring in `PylorosApplication` to construct and inject `ProviderRegistry`, `ToolCatalog`, and `ToolRouter`.
- Kept `ToolRegistry` as a **transitional wrapper** (deprecated/commented) delegating to the new architecture.
- Added collision detection in catalog build (`ToolCatalog` throws on duplicate exposed tool names).
- Preserved naming/compatibility behavior:
  - `pyloros__ping` unchanged
  - IntelliJ tools exposed as `intellij/...`
  - legacy aliases remain callable via router compatibility fallback (`provider.supports(...)` path)
- OAuth behavior unchanged.
- IntelliJ forwarding semantics unchanged.

## Files changed / created
### Changed
- `pyloros-app/src/main/java/com/aresstack/pyloros/PylorosApplication.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolProvider.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/PylorosPingToolProvider.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolRegistry.java`
- `pyloros-upstream-idea/src/main/java/com/aresstack/pyloros/upstream/idea/IdeaToolProvider.java`

### New
- `pyloros-server/src/main/java/com/aresstack/pyloros/provider/ProviderRegistry.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalog.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalogEntry.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolAddress.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolRouter.java`

## Architecture decision touched
- Split former monolithic tool handling (`ToolRegistry`) into separate concerns:
  - provider registration (`ProviderRegistry`)
  - MCP-visible catalog/read model (`ToolCatalog` + `ToolCatalogEntry`)
  - call routing (`ToolRouter`)
- `ToolRegistry` retained only as transitional compatibility abstraction.

## Verification: tests, builds, runtime checks
1. Build:
   - Command: `gradlew.bat clean build`
   - Result: ✅ success (after setting Java 21 and clearing prior jar lock)
2. Shadow jar:
   - Command: `gradlew.bat :pyloros-app:shadowJar`
   - Result: ✅ success
3. Runtime start:
   - Command: `& "C:\Program Files\Zulu\zulu-21\bin\java.exe" -jar .\pyloros-app\build\libs\pyloros.jar`
   - Result: ✅ started (run with `OAUTH_ACCESS_TOKEN=local-test-token` for local MCP auth)
4. Healthcheck:
   - Command: `GET http://127.0.0.1:8081/health`
   - Result: ✅ `200 {"status":"ok"}`
5. `tools/list`:
   - Command: MCP JSON-RPC `tools/list` POST to `/sse` with bearer token
   - Result: ✅ includes `pyloros__ping` and `intellij/get_project_modules`
6. `tools/call pyloros__ping`:
   - Result: ✅ success, returned alive message
7. `tools/call intellij/get_project_modules`:
   - Result: ✅ success, returned module list via IntelliJ forwarding

## Result
✅ successful

## Failure notes / recommended next step
- Initial local-code-search subagent invocation failed because model `qwen2.5-coder:14b` was unavailable in this environment.
- Continued with direct workspace analysis tools to complete assignment.

## Commit / push
- Commit message used: `Introduce tool catalog architecture`
- Exact commit hash: `93a67c6`
- Push performed: `yes` (`origin/main`)
