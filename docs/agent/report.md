# Report: R6-06 review follow-up (view-bound ToolRouter lookup)

## What was verified, changed or implemented?

- Implemented merge-critical hardening for view-scoped dispatch:
  - `ToolCatalog` now provides view-bound access points:
    - `snapshotForView(ToolView)`
    - `findByExternalName(String, ToolView)`
  - `ToolRouter.callTool(toolCall, toolView)` now dispatches using the exact refreshed snapshot for the requested `toolView`, instead of global snapshot lookup.
- Added explicit regression coverage for the requested order-sensitive case:
  - `listTools(PUBLIC)` followed by `callTool(publicOnlyTool, AGENT)` now asserts `Tool not found`.
- Confirmed behavior remains strict for agent visibility boundaries.

## Which files were changed or newly created?

- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalog.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolRouter.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/tool/ToolRouterViewScopingTest.java`

## Which architecture decision was touched?

- Reinforced the R6 `agentToolView` isolation decision by binding lookup+dispatch to the requested view snapshot, eliminating dependence on mutable/global snapshot timing for scoped calls.

## Which tests, builds and runtime checks were executed?

- Baseline before fix:
  - `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.tool.ToolCatalogRoutingTest" --tests "com.aresstack.pyloros.tool.ToolRouterViewScopingTest"` → SUCCESS
- After fix:
  - `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.tool.ToolRouterViewScopingTest" --tests "com.aresstack.pyloros.tool.ToolCatalogRoutingTest"` → SUCCESS
  - `./gradlew --no-daemon :pyloros-server:test` → SUCCESS
- Validation:
  - `parallel_validation` executed
  - CodeQL: SUCCESS, 0 alerts
  - Code Review: FAILED due external backend/header issue (HTTP 400 anthropic-beta header)

## Result: successful or failed

Successful (requested review fix implemented and validated; only external Code Review backend failure remains).

## If failed: exact error and recommended next step

- Non-blocking external validation tool failure:
  - `HTTP error 400: bad request: Unexpected value(s) 'context-1m-2025-08-07' for the 'anthropic-beta' header`
- Recommended next step:
  - Re-run `parallel_validation` when the code-review backend/header issue is resolved.

## Exact commit hash, or No commit created

- `e2398e6`
