What was verified, changed or implemented?
- Verified baseline before changes by running `:pyloros-server:test` and `:pyloros-app:test`.
- Implemented ToolCatalog provider filtering by `ProviderDescriptor.isExposedIn(toolView)` so contributed providers (including plugin providers) participate in the same view-gated catalog flow.
- Added focused integration tests for plugin provider + catalog/router behavior:
  - plugin provider appears in `ProviderRegistry`
  - plugin tool visible in PUBLIC view
  - plugin tool hidden in non-exposed view
  - plugin/native collision raises validation error (no silent overwrite)
  - plugin tool call is routed through `ToolRouter`
  - disabled plugin contributes no tools

Which files were changed or newly created?
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolCatalog.java`
- Created: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginProviderCatalogIntegrationTest.java`

Which architecture decision was touched?
- Tool visibility is now enforced centrally by ToolCatalog using ProviderRegistry descriptors (`exposedViews`), so plugin-contributed providers are treated the same as native providers in catalog construction.

Which tests, builds and runtime checks were executed?
- `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test` (before changes)
- `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.plugin.PluginProviderCatalogIntegrationTest" --tests "com.aresstack.pyloros.tool.ToolCatalogRoutingTest"`
- `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test` (after changes)
- `parallel_validation`:
  - CodeQL: success (0 alerts)
  - Code Review: failed due tool-side HTTP 400 header error

Result: failed
If failed: exact error and recommended next step
- Error: `Code review tool encountered an issue: failed to complete: HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header.`
- Recommended next step: rerun `parallel_validation` after the external Code Review service/header issue is resolved; no code defects were reported by CodeQL.

Exact commit hash, or No commit created
- `6170f8a`
