# Report: R6-07 comment follow-up — cross-ACP manager-agent recursion gap

## What was verified, changed or implemented?
- Verified and addressed the reported gap: validation previously considered ACP provider IDs only, but not whether another ACP provider is exposed in the manager agent's selected `agentToolView`.
- Extended `AcpProviderFactory` to compute ACP provider exposure per view (`view -> set(providerIds)`) and pass it into `AgentToolViewValidator`.
- Extended `AgentToolViewValidator` signature and logic to reject configurations where `agentToolView` includes any *other* ACP provider exposed in that view.
- Added explicit error text including provider id, agentToolView, and the colliding ACP provider id:
  - `... includes ACP provider 'other-acp' and may trigger recursive agent invocation.`
- Preserved existing protections (public-view ban, self-reference ban, provider-id reference ban, own exposeInViews collision ban).
- Added tests:
  - `AgentToolViewValidatorTest` now covers cross-ACP exposure rejection.
  - New `AcpProviderFactoryTest` verifies runtime registration behavior:
    - manager provider rejected when another ACP provider is exposed in manager view,
    - positive case still works when no ACP provider is exposed in manager view.
- Updated manager-agent smoke test docs with cross-ACP exposure negative scenario.

## Which files were changed or newly created?
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-app/src/main/java/com/aresstack/pyloros/config/AcpProviderFactory.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/main/java/com/aresstack/pyloros/acp/AgentToolViewValidator.java`
- Changed: `/home/runner/work/pyloros/pyloros/pyloros-server/src/test/java/com/aresstack/pyloros/acp/AgentToolViewValidatorTest.java`
- New: `/home/runner/work/pyloros/pyloros/pyloros-app/src/test/java/com/aresstack/pyloros/config/AcpProviderFactoryTest.java`
- Changed: `/home/runner/work/pyloros/pyloros/docs/smoke-test/r6-manager-agent-smoke-test.md`

## Which architecture decision was touched?
- Reinforced the R6 manager-agent isolation decision: manager agent tool view must exclude ACP providers that can induce recursive agent invocation, including cross-ACP exposure through shared views.

## Which tests, builds and runtime checks were executed?
- Baseline before edits:
  - `./gradlew --no-daemon :pyloros-server:test` → SUCCESS
- Targeted checks after edits:
  - `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.acp.AgentToolViewValidatorTest" :pyloros-app:test --tests "com.aresstack.pyloros.config.AcpProviderFactoryTest"` → SUCCESS
- Broader module checks:
  - `./gradlew --no-daemon :pyloros-server:test :pyloros-app:test` → SUCCESS
- CI/validation checks:
  - GitHub Actions runs inspected for base branch `copilot/release-6-java-21-acp-manager-agent` and current branch `copilot/r6-07-harden-agent-tool-view`
  - `parallel_validation`:
    - CodeQL Security Scan → SUCCESS (0 alerts)
    - Code Review → FAILED due external HTTP 400 header issue

## Result: successful or failed
- Successful (requested cross-ACP recursion protection implemented and verified).

## If failed: exact error and recommended next step
- External validation tool issue (non-code):
  - `HTTP error 400: bad request: Unexpected value(s) 'context-1m-2025-08-07' for the 'anthropic-beta' header`
- Recommended next step:
  - Re-run `parallel_validation` once the external Code Review service/header issue is resolved.

## Exact commit hash, or No commit created
- `523502c`
