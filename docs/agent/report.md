What was verified, changed or implemented?
- Addressed PR comment #4358403129 for issue #25 by adding a documented **Release 4 smoke test** in a user-facing repository document.
- Added a new `Release 4 smoke test (documented checklist)` section to `/docs/plugin-development.md`.
- The smoke-test documentation now explicitly covers:
  - example plugin path
  - ServiceLoader discovery
  - plugin enable/disable config behavior
  - tools/list visibility expectations
  - ToolRouter call expectation
  - expected external tool naming (`example-tools__echo`, and `/` separator variant)
- Verified the documented commands run successfully in this repository.

Which files were changed or newly created?
- Changed:
  - `/home/runner/work/pyloros/pyloros/docs/plugin-development.md`
  - `/home/runner/work/pyloros/pyloros/docs/agent/report.md`
- Created: none

Which architecture decision was touched?
- None. This change documents existing R4 plugin architecture and verification flow; no runtime architecture or code path was changed.

Which tests, builds and runtime checks were executed?
- `./gradlew --no-daemon :pyloros-example-plugin:compileJava :pyloros-server:test --tests "com.aresstack.pyloros.plugin.ServiceLoaderDiscoveryTest" :pyloros-app:test --tests "com.aresstack.pyloros.ExampleEchoPluginIntegrationTest"`
- CI investigation via GitHub Actions MCP tools for recent runs and failed-run logs retrieval:
  - `list_workflow_runs`
  - `list_workflow_jobs` (run `26414259755`)
  - `get_job_logs` failed-only (run `26414259755`)

Result: successful

If failed: exact error and recommended next step
- No failures in the documented smoke-test verification command.
- GitHub Actions MCP check returned no failed jobs for inspected run `26414259755`; no immediate action required from this PR comment task.

Exact commit hash, or No commit created
- No commit created
