# Report: R6-04 Spike ACP Java SDK and MCP Java SDK integration for manager agent

## What was verified, changed or implemented?

1. Verified repository baseline on the active R6-04 spike branch and ran baseline server tests before editing.
2. Researched current ACP Java SDK and MCP Java SDK release/state using upstream GitHub releases and Maven metadata.
3. Added a dedicated R6-04 decision spike document with:
   - recommended SDKs and pinned versions for R6,
   - minimal ACP session bootstrap sketch,
   - minimal MCP client connection sketch against Pyloros endpoint,
   - risks for Java 21, packaging/distribution, and open questions,
   - rationale for compile/smoke blockade scope.
4. Added one cross-link from the existing R6 manager-agent architecture document to the new spike decision document.
5. Re-ran targeted server tests after documentation changes.

## Which files were changed or newly created?

| File | Change |
|------|--------|
| `docs/requirements/007-r6-04-acp-mcp-java-sdk-spike.md` | New R6-04 decision spike document (ACP/MCP SDK choice, versions, risks, sketches, open questions) |
| `docs/requirements/006-acp-manager-agent.md` | Added cross-reference to the new R6-04 spike decision document |
| `docs/agent/report.md` | Replaced with this report |

## Which architecture decision was touched?

- R6 manager-agent SDK direction: use ACP and MCP Java SDKs as external manager-agent runtime dependencies while keeping Pyloros core as gateway/aggregator (no in-core agent logic).

## Which tests, builds and runtime checks were executed?

- `./gradlew --no-daemon :pyloros-server:test` (baseline, before changes) → SUCCESS
- `./gradlew --no-daemon :pyloros-server:test` (after changes) → SUCCESS
- `parallel_validation` (run twice):
  - CodeQL → skipped as trivial (documentation-only)
  - Code Review → failed due external tooling HTTP 400 header error

## Result

Successful

## If failed: exact error and recommended next step

- Non-blocking validation tooling failure from Code Review in `parallel_validation`:
  - `HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header`
- Recommended next step: re-run `parallel_validation` Code Review once upstream tool/header issue is resolved.

## Exact commit hash, or No commit created

46a7595
