What was verified, changed or implemented?
- Reviewed the PR feedback and implemented the runtime activation gap fix in bootstrap: `PylorosApplication` no longer uses `PluginRegistry.load(Set.of())`; it now loads `PluginsConfig` from the discovered `mcp.json` and uses `PluginActivationResolver` with `PluginRegistry.load(resolver)`.
- Added an application/bootstrap-path test that uses real ServiceLoader discovery in `pyloros-app` test runtime and verifies an explicitly disabled plugin is `DISABLED` and contributes no provider.
- Added catalog semantics tests to verify provider-level `exposedViews` gating remains consistent with provider `listTools(toolView)` behavior across provider types (NATIVE/MCP/ACP) and plugin-like providers.

Which files were changed or newly created?
- Changed:
  - `/home/runner/work/pyloros/pyloros/pyloros-app/src/main/java/com/aresstack/pyloros/PylorosApplication.java`
- Created:
  - `/home/runner/work/pyloros/pyloros/pyloros-app/src/test/java/com/aresstack/pyloros/PylorosApplicationPluginBootstrapTest.java`
  - `/home/runner/work/pyloros/pyloros/pyloros-app/src/test/java/com/aresstack/pyloros/TestBootstrapPlugin.java`
  - `/home/runner/work/pyloros/pyloros/pyloros-app/src/test/resources/META-INF/services/com.aresstack.pyloros.plugin.PylorosPlugin`
  - `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/tool/ToolCatalogViewFilteringSemanticsTest.java`

Which architecture decision was touched?
- Plugin loading in runtime bootstrap is now configuration-driven via the existing R4-03 activation model (`PluginsConfig` + `PluginActivationResolver`) and not an always-enabled fallback path.
- ToolCatalog provider-level view gating semantics were verified with explicit cross-provider-type tests to ensure compatibility with provider-side `listTools(toolView)` filtering.

Which tests, builds and runtime checks were executed?
- Baseline before changes:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- Targeted checks after changes:
  - `./gradlew --no-daemon :pyloros-app:test --tests "com.aresstack.pyloros.PylorosApplicationPluginBootstrapTest" :pyloros-server:test --tests "com.aresstack.pyloros.tool.ToolCatalogViewFilteringSemanticsTest" --tests "com.aresstack.pyloros.plugin.PluginProviderCatalogIntegrationTest"`
- Broader verification:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- Validation:
  - `parallel_validation` run twice
  - CodeQL: success (0 alerts)
  - Code Review: tool-side HTTP 400 failure

Result: failed
If failed: exact error and recommended next step
- Error (Code Review tool): `HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header.`
- Recommended next step: rerun `parallel_validation` once the external Code Review service/header issue is resolved. No CodeQL security issues were reported.

Exact commit hash, or No commit created
- `d06ad1e`
