What was verified, changed or implemented?
- Refactored ACP classes to remove all Java records in the allowed ACP directories.
- Replaced ACP-only uses of List.of(), Map.of(), List.copyOf(), Map.copyOf(), and Stream.toList() with Java 8 compatible alternatives.
- Removed the AcpVirtualToolProvider inner TaskLookup record and replaced it with a null-plus-error-holder lookup helper.
- Updated ACP tests for the class conversions and ACP-only collection API changes.
- Verified ACP compilation before and after the refactor with the requested Gradle targets.
- Ran parallel validation; CodeQL passed and code review failed due an external tool HTTP 400 header error.

Which files were changed or newly created?
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpAgentClient.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpExecutionConfiguration.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpProcessConfiguration.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpProcessLauncher.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpProviderConfiguration.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpToolDefinitions.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpToolTimeoutConfiguration.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpVirtualToolProvider.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AgentTask.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AgentTaskId.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/InMemoryAgentTaskRepository.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/PendingPermission.java
- Changed: pyloros-server/src/test/java/com/aresstack/pyloros/acp/AcpVirtualToolProviderTest.java
- Changed: pyloros-server/src/test/java/com/aresstack/pyloros/acp/AgentTaskTest.java

Which architecture decision was touched?
- None. The existing ACP integration boundaries remained unchanged; this was a local ACP model and collection API refactor only.

Which tests, builds and runtime checks were executed?
- ./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava (baseline) — successful
- ./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava (after changes) — successful
- parallel_validation — CodeQL successful, code review tool failed externally

Result: successful

If failed: exact error and recommended next step
- Non-blocking validation tool issue only: Code review failed with HTTP error 400: bad request: Unexpected value(s) `context-1m-2025-08-07` for the `anthropic-beta` header.
- Recommended next step: rerun the review validation after the external service/header issue is fixed.

Exact commit hash, or No commit created
- No commit created
