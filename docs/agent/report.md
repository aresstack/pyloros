What was verified, changed or implemented?
- Addressed the 3 blocking R3 issues from comment 4353476806:
  1. Fixed ObjectMapper to use findAndRegisterModules() for Instant serialization in PendingPermission
  2. Added cancellation checks before process launch, createSession, and sendPrompt to prevent race conditions
  3. Enhanced AcpEventMapper to handle stopReason-only events (permission_required, end_turn) without requiring type field
- Verified compilation and full test suite pass

Which files were changed or newly created?
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpVirtualToolProvider.java
- Changed: pyloros-server/src/main/java/com/aresstack/pyloros/acp/AcpEventMapper.java

Which architecture decision was touched?
- ACP event handling now supports stopReason-only updates per ACP protocol spec
- Task cancellation is now checked at all blocking points to prevent zombie processes

Which tests, builds and runtime checks were executed?
- ./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava — successful
- ./gradlew --no-daemon :pyloros-server:test — successful (all tests pass)
- parallel_validation — CodeQL successful (0 alerts), code review failed externally with HTTP 400

Result: successful

If failed: exact error and recommended next step
- Non-blocking validation tool issue only: code review failed with HTTP error 400: bad request: Unexpected value(s) context-1m-2025-08-07 for the anthropic-beta header.
- Recommended next step: wait for external tool/header issue to be resolved.

Exact commit hash, or No commit created
- 0e59f7b
