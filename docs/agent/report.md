What was verified, changed or implemented?
- Implemented R4-07 with a dedicated example plugin module `pyloros-example-plugin` that uses the existing R4 plugin/runtime flow (`PylorosPlugin` -> `PluginRegistry` -> `ProviderRegistry` -> `ToolCatalog` -> `ToolRouter`).
- Added a minimal deterministic example tool (`echo`) exposed by provider `example-tools`, yielding external name `example-tools__echo` with default separator and `example-tools/echo` when `/` separator is configured.
- Registered the example plugin via `META-INF/services/com.aresstack.pyloros.plugin.PylorosPlugin` for ServiceLoader discovery.
- Added focused integration tests that prove: discovery, catalog visibility, router execution/echo behavior, and explicit disable behavior.
- Added developer-facing plugin documentation covering plugin ID, ServiceLoader registration, Gradle setup, plugin configuration, visibility/naming/collision behavior, and local verification steps.
- Executed baseline and post-change test runs successfully.

Which files were changed or newly created?
- Changed:
  - `/home/runner/work/pyloros/pyloros/settings.gradle`
  - `/home/runner/work/pyloros/pyloros/pyloros-app/build.gradle`
  - `/home/runner/work/pyloros/pyloros/README.md`
- Created:
  - `/home/runner/work/pyloros/pyloros/pyloros-example-plugin/build.gradle`
  - `/home/runner/work/pyloros/pyloros/pyloros-example-plugin/src/main/java/com/aresstack/pyloros/example/plugin/ExampleEchoPlugin.java`
  - `/home/runner/work/pyloros/pyloros/pyloros-example-plugin/src/main/java/com/aresstack/pyloros/example/plugin/ExampleEchoToolProvider.java`
  - `/home/runner/work/pyloros/pyloros/pyloros-example-plugin/src/main/resources/META-INF/services/com.aresstack.pyloros.plugin.PylorosPlugin`
  - `/home/runner/work/pyloros/pyloros/pyloros-app/src/test/java/com/aresstack/pyloros/ExampleEchoPluginIntegrationTest.java`
  - `/home/runner/work/pyloros/pyloros/docs/plugin-development.md`

Which architecture decision was touched?
- Reused the existing plugin architecture and runtime pipeline from R4-05/R4-06 (no alternate plugin API/catalog/router path introduced).
- Kept plugin activation and contribution semantics aligned with `PluginActivationResolver`, `PluginRegistry`, `ToolCatalog`, and `ToolRouter`.
- Added explicit documentation for view-based exposure and external-name collision expectations in the current ToolCatalog model.

Which tests, builds and runtime checks were executed?
- Baseline before changes:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- Targeted checks after implementation:
  - `./gradlew --no-daemon :pyloros-example-plugin:compileJava :pyloros-app:test --tests "com.aresstack.pyloros.ExampleEchoPluginIntegrationTest"`
- Broader regression checks:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- Validation tool:
  - `parallel_validation` executed twice
  - CodeQL: success (0 alerts, second run skipped due to no new changes)
  - Code Review: failed due to external HTTP 400 service/header issue

Result: failed
If failed: exact error and recommended next step
- Error (Code Review tool): `HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header.`
- Recommended next step: rerun `parallel_validation` (Code Review) once the external service/header issue is resolved. Current code/test verification and CodeQL checks are passing.

Exact commit hash, or No commit created
- `8f19a44`
