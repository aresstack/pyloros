What was verified, changed or implemented?
- Updated Release 6 documentation to match issue scope `aresstack/pyloros#59` without overstating implementation status.
- Documented protocol roles clearly (MCP for tools/resources, ACP for agent sessions, A2A as future option).
- Clarified that ACP provider infrastructure in this repository is implemented, while a standalone manager-agent runtime remains a separate deliverable.
- Updated R6 smoke-test document with explicit manual flow and a separate later-automatable smoke-test sequence.

Which files were changed or newly created?
- Changed:
  - /home/runner/work/pyloros/pyloros/docs/requirements/006-acp-manager-agent.md
  - /home/runner/work/pyloros/pyloros/docs/smoke-test/r6-manager-agent-smoke-test.md
  - /home/runner/work/pyloros/pyloros/docs/agent/report.md
- Created: none

Which architecture decision was touched?
- Release 6 architecture positioning: Pyloros remains MCP capability gateway/aggregator; manager-agent orchestration is external via ACP and not yet represented as a completed standalone runtime in this repository.

Which tests, builds and runtime checks were executed?
- ./gradlew --no-daemon :pyloros-server:test (before documentation edits)
- ./gradlew --no-daemon :pyloros-server:test (after documentation edits)

Result: successful

If failed: exact error and recommended next step
- No failures.

Exact commit hash, or No commit created
- 0d22071
