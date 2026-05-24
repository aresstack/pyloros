What was verified, changed or implemented?
- Replaced placeholder ACP execution in `AcpVirtualToolProvider` with the real process/session flow for `run_task`, `start_task`, timeout handling, and cancellation.
- Added active ACP process tracking, audit logging, synchronous wait via Vert.x `executeBlocking`, background execution for `start_task`, and terminal-state result/error payloads.
- Added `AgentToolViewValidator` to reject direct and indirect ACP recursion through `agentToolView`.
- Added `FakeAcpAgent` plus integration and provider tests covering success, failure, permission, large-output, timeout, and cancellation flows.
- Verified requested ACP compile and test commands after the implementation.
- Ran parallel validation; CodeQL passed and code review failed due an external HTTP 400 header issue.

Which files were changed or newly created?
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpProcessLauncher.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpToolDefinitions.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpVirtualToolProvider.java
- New: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AgentToolViewValidator.java
- Changed: pyloros-server/src/test/java/com/aresstack/pyloros/acp/AcpVirtualToolProviderTest.java
- New: pyloros-server/src/test/java/com/aresstack/pyloros/acp/AcpIntegrationTest.java
- New: pyloros-server/src/test/java/com/aresstack/pyloros/acp/AgentToolViewValidatorTest.java
- New: pyloros-server/src/test/java/com/aresstack/pyloros/acp/FakeAcpAgent.java

Which architecture decision was touched?
- ACP task execution now follows the real ACP stdio lifecycle inside `AcpVirtualToolProvider`, while recursion prevention is enforced as a separate ACP validation concern.

Which tests, builds and runtime checks were executed?
- ./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava (baseline) — successful
- ./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava (after changes) — successful
- ./gradlew --no-daemon :pyloros-server:test — successful
- parallel_validation — CodeQL successful, code review tool failed externally

Result: successful

If failed: exact error and recommended next step
- Non-blocking validation tool issue only: Code review failed with HTTP error 400: bad request: Unexpected value(s) `context-1m-2025-08-07` for the `anthropic-beta` header.
- Recommended next step: rerun the review validation after the external service/header issue is fixed.

Exact commit hash, or No commit created
- No commit created
