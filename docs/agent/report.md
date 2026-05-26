# Report: R6-03 review follow-up (README/doc distribution wording)

## What was verified, changed or implemented?

- Verified the actionable @copilot PR comment requesting README restoration and distribution wording precision.
- Investigated Actions workflow runs for both stacked-base branch `copilot/release-6-java-21-acp-manager-agent` and current branch `copilot/define-java-21-acp-manager-agent`.
- Restored `README.md` content to the integration-branch baseline and reapplied only minimal R6-03 additions.
- Updated README wording to describe `pyloros-manager-agent` as a separate Gradle `application` distribution/start-script module (not fat/self-contained `java -jar`).
- Kept ACP stdio-safety note (`stderr` logging, `stdout` reserved for ACP JSON-RPC).
- Added a small manager-agent distribution section with `installDist` and generated script path.
- Updated `docs/requirements/006-acp-manager-agent.md` wording from process/JAR phrasing to application-distribution/bootstrap-subproject phrasing.

## Which files were changed or newly created?

- `README.md` (changed)
- `docs/requirements/006-acp-manager-agent.md` (changed)
- `docs/agent/report.md` (overwritten for current task report)

## Which architecture decision was touched?

- R6-03 module boundary/documentation decision: manager-agent stays a separate Java 21 bootstrap subproject packaged as Gradle application distribution/start scripts.
- ACP stdio channel safety remains explicit: logs to `stderr`, `stdout` reserved for ACP JSON-RPC.

## Which tests, builds and runtime checks were executed?

- CI/workflow inspection via GitHub Actions MCP:
  - Listed workflow runs for `copilot/release-6-java-21-acp-manager-agent`.
  - Listed workflow runs for `copilot/define-java-21-acp-manager-agent`.
  - Inspected run `26425479051` details and failed-job logs (`failed_jobs: 0`, no failed jobs in that run).
- Local validation:
  - `./gradlew --no-daemon :pyloros-manager-agent:compileJava :pyloros-manager-agent:installDist` → SUCCESS

## Result: successful or failed

Successful

## If failed: exact error and recommended next step

- No blocking failure for this task.

## Exact commit hash, or No commit created

- No commit created
