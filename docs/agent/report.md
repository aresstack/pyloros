# Report: R6-01 Retire R5 LangChain-in-core artifacts

## What was verified, changed or implemented?

1. Verified core `ProviderType` is already reduced to `NATIVE`, `MCP`, `ACP`, `UNKNOWN`.
2. Verified no productive Java/runtime references to `LANGCHAIN`, `COMPOSITE`, or `REST` remain.
3. Updated legacy R5 LangChain requirements document to explicitly mark the implementation milestones as historical/obsolete and direct readers to the R6 ACP manager-agent document.
4. Re-ran compile/tests to confirm no regressions.

## Which files were changed or newly created?

| File | Change |
|------|--------|
| `docs/requirements/pyloros-langchain-extension.md` | Added explicit obsolete notice above implementation milestones with pointer to `docs/requirements/006-acp-manager-agent.md` |
| `docs/agent/report.md` | Replaced with this task report |

## Which architecture decision was touched?

- Continued R6 decision to retire Release-5 LangChain-in-core concept in favor of ACP Manager Agent architecture (`docs/requirements/006-acp-manager-agent.md`).

## Which tests, builds and runtime checks were executed?

- `./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava :pyloros-server:test` (baseline) → SUCCESS
- `./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava :pyloros-server:test` (after doc change) → SUCCESS
- `parallel_validation` → CodeQL skipped as trivial (SUCCESS), Code Review tool failed due external HTTP 400 header error

## Result

Successful

## If failed: exact error and recommended next step

- Non-blocking tooling failure during Code Review inside `parallel_validation`:
  - `HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header`
- Recommended next step: re-run Code Review validation once the external integration/header issue is resolved.

## Exact commit hash, or No commit created

861c21d
