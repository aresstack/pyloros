What was verified, changed or implemented?
- Implemented R4-08 regression hardening additions in plugin catalog integration tests.
- Added regression coverage that a disabled plugin does not hide existing native tools (`pyloros__ping`).
- Added regression coverage that an invalid plugin contribution is isolated and does not break existing MCP provider catalog visibility or ToolRouter calls.
- Verified existing plugin/runtime regression suites still pass.

Which files were changed or newly created?
- Changed:
  - `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginProviderCatalogIntegrationTest.java`
  - `/home/runner/work/pyloros/pyloros/docs/agent/report.md`
- Created: none

Which architecture decision was touched?
- Confirmed and reinforced the existing R4 decision: plugins and plugin failures stay isolated and all tools (native, MCP, plugin) continue to flow through the same `ToolCatalog` + `ToolRouter` path without regressions.

Which tests, builds and runtime checks were executed?
- Baseline before changes:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- After changes (targeted):
  - `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.plugin.PluginProviderCatalogIntegrationTest"`
- After changes (full required module suites):
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`

Result: successful

If failed: exact error and recommended next step
- Intermediate failed run (fixed in this session):
  - `:pyloros-server:compileTestJava` failed with `cannot find symbol: class InvalidContributionPlugin` in `PluginProviderCatalogIntegrationTest`.
  - Cause: helper class was initially nested in the wrong scope.
  - Fix applied: moved `InvalidContributionPlugin` to top-level nested class scope inside the test class.
  - Recommended next step: none.

Exact commit hash, or No commit created
- No commit created
