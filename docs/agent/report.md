What was verified, changed or implemented?
- Addressed PR review blockers for issue #24 by fixing developer-facing documentation quality and runtime dependency behavior.
- Replaced user-facing absolute CI/runner paths with repository-relative paths in README and plugin guide.
- Corrected naming documentation to be consistent with actual provider/tool naming:
  - default external name: `example-tools__echo`
  - slash separator mode: `example-tools/echo`
- Changed `pyloros-app` dependency on `pyloros-example-plugin` from `implementation` to `testImplementation` so the example plugin is not present in production runtime classpath by default.
- Explicitly documented this test-only runtime behavior in `docs/plugin-development.md`.

Which files were changed or newly created?
- Changed:
  - `README.md`
  - `docs/plugin-development.md`
  - `pyloros-app/build.gradle`

Which architecture decision was touched?
- Preserved the existing R4 plugin API/runtime path.
- Adjusted packaging/runtime-classpath decision: example plugin remains available for tests and documentation but is not bundled into production `pyloros-app` runtime by default.

Which tests, builds and runtime checks were executed?
- Baseline before fixes:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- Targeted checks after fixes:
  - `./gradlew --no-daemon :pyloros-app:test --tests "com.aresstack.pyloros.ExampleEchoPluginIntegrationTest" --tests "com.aresstack.pyloros.PylorosApplicationPluginBootstrapTest"`
- Broader checks after fixes:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test`
- Validation:
  - `parallel_validation`
  - CodeQL: success (no analyzable code changes)
  - Code Review: external HTTP 400 failure

Result: failed
If failed: exact error and recommended next step
- Error (Code Review tool): `HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header.`
- Recommended next step: rerun `parallel_validation` once the external Code Review service/header issue is resolved.

Exact commit hash, or No commit created
- No commit created
