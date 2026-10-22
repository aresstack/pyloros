# Report — sporadic MCP Error in input stream

## Active assignment and approval

The user approved this MCP stream-robustness task on 2026-10-22 and authorized replacing the previous active assignment, committing the corrections, and pushing the existing feature branch. The previous Maven Central publishing task remains in Git history; this assignment now describes the active task.

- Repository: aresstack/pyloros
- Base: 217fe5c96a4eb90ef64a84a2f9294e5ad847d5cb
- Branch: fix/mcp-stream-robustness-20261022
- Pull request: https://github.com/aresstack/pyloros/pull/108 (Draft; not merged)
- The user confirms that the live server runs the current main version at the stated baseline, including the path alignment made to match Ask AI. A stale JAR found in the local build directory is not evidence of the deployed version.

## What was verified, changed or implemented?

### Findings and fixes

1. **Null values in JSON-RPC envelopes.** HttpJson used Map.of(...), which rejects null values. A null ID, null result, or null exception message could trigger a NullPointerException while an asynchronous failure callback was building an error response. In that case the HTTP response could remain uncompleted. This is a strong code-level candidate for a stalled stream, but it has not been proven to be the production trigger.
2. **MCP transport conformance.** The public Streamable HTTP endpoint now responds to GET with 405 Method Not Allowed and Allow: POST because no server-to-client SSE stream is implemented. Notification requests receive 202 Accepted without a response body.
3. **IDEA SSE reconnect lifecycle.** Reconnect scheduling is guarded against duplicate attempts; attempt generations prevent callbacks from obsolete connections from overwriting current state; pending reconnect timers are cancelled on stop.
4. **SSE text decoding.** A shared decoder handles UTF-8 characters split across network buffers and normalizes line endings without inventing event boundaries.

The independent webserver and TLS configuration were not changed.

### Files changed or added

Changed:
- pyloros-server/src/main/java/com/aresstack/pyloros/http/HttpJson.java
- pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java
- pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/SseMcpUpstreamClient.java
- pyloros-upstream-idea/src/main/java/com/aresstack/pyloros/upstream/idea/IdeaSseSession.java

Added:
- pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/SseTextDecoder.java
- pyloros-server/src/test/java/com/aresstack/pyloros/http/McpErrorResponseContractTest.java
- pyloros-server/src/test/java/com/aresstack/pyloros/http/McpTransportConformanceTest.java
- pyloros-server/src/test/java/com/aresstack/pyloros/upstream/mcp/SseTextDecoderTest.java
- pyloros-upstream-idea/src/test/java/com/aresstack/pyloros/upstream/idea/IdeaSseSessionLifecycleTest.java

Documentation corrected for this task:
- docs/agent/assignment.md
- docs/agent/report.md

## Architecture decisions

- The public MCP endpoint supports Streamable HTTP without a server-initiated GET SSE stream.
- SSE decoding is shared between the generic SSE upstream and IDEA SSE session.
- IDEA connection state belongs to one generation-numbered attempt; callbacks from obsolete attempts cannot invalidate the current endpoint.
- Module boundaries and optional-upstream startup behaviour remain unchanged.

## Tests, build and runtime checks

Reported local verification:
- ./gradlew build — BUILD SUCCESSFUL, 352 tests, 0 failures, 0 skipped.
- Targeted tests: McpErrorResponseContractTest (6), McpTransportConformanceTest (5), IdeaSseSessionLifecycleTest (5), and SseTextDecoderTest (9).
- The :pyloros-auth-oauth:test suite passed, including existing root-endpoint compatibility tests.
- Mutation check: reverting reconnect/generation guards makes duplicateDropEventsScheduleOnlyOneReconnect and lateCallbackFromSupersededAttemptDoesNotClearFreshEndpoint fail.
- repeatedServerSideDropsDoNotMultiplyConnections and stopCancelsPendingReconnect also pass with the old logic; they are sanity tests rather than regression tests.
- No live-instance or ChatGPT runtime test was performed.

## Result

The code-level defects listed above have been addressed in the feature branch. The full build is reported successful. The exact production cause remains unproven until a failure timestamp can be correlated with Pyloros and Caddy logs.

## Remaining risks

- ToolView.named() accepts any non-blank name, so the null-ID -32602 branch is not reachable through ordinary HTTP requests; the null-ID response contract is tested through a test-only probe.
- StreamableHttpMcpUpstreamClient remains outside this change set. Known areas for a separate follow-up are failed initialization marking the client initialized, concurrent first calls racing to set sessionId, reading SSE responses with bodyAsString() instead of incrementally, and failure to reinitialize after a 404 for an expired session.
- IdeaSseSession has no idle timeout for a silently half-open SSE connection.
- The exact trigger for the user's intermittent Error in input stream still needs production log correlation.

## Commits and PR

Implementation commits on the feature branch:
- 70b43e3 — Harden HTTP error handling against null values
- 546578e — Make the MCP endpoint conform to Streamable HTTP
- 7454b59 — Serialize IdeaSseSession reconnects and guard against late callbacks
- 5856baf — Decode SSE streams across network buffer boundaries
- 93dda7d10fe6954c3db78dccb4367b9ab87f6022 — Initial investigation report (the report and assignment were subsequently corrected as requested)

The branch has been pushed and PR #108 is open as Draft. No merge, deployment, or release was performed.
