# Report: R6-07 Harden agent tool view and recursion protection for manager agent

## What was verified, changed or implemented?
- Verified baseline state by running `:pyloros-server:test` before changes.
- Hardened `AgentToolViewValidator` error messages so invalid manager-agent `agentToolView` collisions explicitly describe recursion/self-recursion risk and involved provider/view.
- Kept and validated the existing protection rules (`public` forbidden, self-provider ID forbidden, other ACP provider ID forbidden, own `exposeInViews` collision forbidden).
- Extended unit coverage in `AgentToolViewValidatorTest` with manager-focused scenarios:
  - valid isolated manager view is accepted,
  - `public` view is rejected,
  - self-reference recursion is rejected,
  - reference to another ACP provider is rejected,
  - own exposed-view collision is rejected.
- Updated manager-agent smoke-test doc snippets to match the new clearer validation messages.

## Which files were changed or newly created?
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/acp/AgentToolViewValidator.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/acp/AgentToolViewValidatorTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/docs/smoke-test/r6-manager-agent-smoke-test.md`

## Which architecture decision was touched?
- Reinforced the R6 manager-agent recursion protection decision: ACP manager agents must only use safe non-public tool views and must not resolve to self/other ACP provider recursion paths.

## Which tests, builds and runtime checks were executed?
- `./gradlew --no-daemon :pyloros-server:test` (baseline before changes) → SUCCESS
- `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.acp.AgentToolViewValidatorTest"` → SUCCESS
- `./gradlew --no-daemon :pyloros-server:test` (after changes) → SUCCESS
- `parallel_validation`:
  - CodeQL Security Scan → SUCCESS (0 alerts)
  - Code Review → FAILED due external service HTTP 400 header issue

## Result: successful or failed
- Successful (code/test/doc changes implemented and validated; only external Code Review backend error remained).

## If failed: exact error and recommended next step
- External validation tool failure (non-code):
  - `HTTP error 400: bad request: Unexpected value(s) 'context-1m-2025-08-07' for the 'anthropic-beta' header`
- Recommended next step:
  - Re-run `parallel_validation` once the external Code Review service/header issue is resolved.

## Exact commit hash, or No commit created
- `faebbaf`
