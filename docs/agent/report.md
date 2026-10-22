# Report — sporadic "Error in input stream" on the public MCP endpoint

## What was verified, changed or implemented?

Investigated the sporadic `Error in input stream` reported by the ChatGPT MCP
client and fixed the defects found in Pyloros. The webserver and its TLS
configuration were deliberately left untouched.

Branch: `fix/mcp-stream-robustness-20261022`, branched off `origin/main`
(`217fe5c`). The local `main` was 3 commits behind `origin/main` at the start
and was fast-forwarded first; the working tree was clean.

### Root cause ranking

1. **`HttpJson` built JSON-RPC envelopes with `Map.of(...)`**, which rejects
   null values. A null request id, a null exception message or a null tool
   result threw a `NullPointerException`. Thrown inside an asynchronous
   `onFailure` callback — the normal case for `tools/list` and `tools/call` —
   Vert.x Web can no longer convert it into a 500, so the response was never
   ended and the client stalled until its proxy cut the connection. The
   original upstream error was swallowed, which is why the logs showed
   nothing. This is the strongest match for a failure that appears only after
   a long, initially healthy run: it needs an upstream error with no message.
2. **`IdeaSseSession` reconnect handling.** A single drop fires both
   `exceptionHandler` and `endHandler`, and `scheduleReconnect()` had no
   guard, so each drop cycle started two attempts, each re-registering both
   handlers. `markStaleAndFailPending()` also cleared `currentEndpoint`
   unconditionally, letting a late callback from an abandoned connection wipe
   the endpoint a healthy connection had just discovered.
3. **Transport non-conformance.** GET on the MCP endpoint answered with a
   deprecated HTTP+SSE `event: endpoint` frame; notifications were answered
   with a JSON body instead of a bodyless 202.
4. **UTF-8 decoding per network buffer** in both SSE readers, corrupting
   multi-byte characters split across buffers, and per-buffer `\r\n`
   normalization fabricating event boundaries.

### Deployed-jar check

`build/gh-artifact-check/pyloros.jar` (17 Jun) predates this branch. Its
`McpRoutes` still mounts `/sse` and `/pyloros` rather than `/`, and its GET
handler calls `write()` twice **without ever calling `end()`**, leaving every
GET SSE response dangling. If production runs a jar of this vintage, there is
both an endpoint mismatch against the current `chatgpt-plugin/mcp.json` (which
uses `/`) and a response/socket leak on every GET. Deployed version unknown —
needs checking against the running instance.

## Which files were changed or newly created?

Changed:

- `pyloros-server/src/main/java/com/aresstack/pyloros/http/HttpJson.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/SseMcpUpstreamClient.java`
- `pyloros-upstream-idea/src/main/java/com/aresstack/pyloros/upstream/idea/IdeaSseSession.java`

New:

- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/SseTextDecoder.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/http/McpErrorResponseContractTest.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/http/McpTransportConformanceTest.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/upstream/mcp/SseTextDecoderTest.java`
- `pyloros-upstream-idea/src/test/java/com/aresstack/pyloros/upstream/idea/IdeaSseSessionLifecycleTest.java`
  (first test source set in this module)

## Which architecture decision was touched?

- The public MCP endpoint is Streamable HTTP only. GET no longer offers any
  SSE stream and declines with `405` plus `Allow: POST`. Should Pyloros ever
  need server-initiated JSON-RPC messages, a real GET SSE stream with
  keepalive and connection cleanup must be implemented — not the old
  `endpoint` frame.
- SSE stream decoding is now a shared concern in
  `com.aresstack.pyloros.upstream.mcp.SseTextDecoder`, used by both the
  generic SSE upstream client and the IDEA session.
- `IdeaSseSession` connection state is owned by exactly one generation-numbered
  attempt. No change to module boundaries; optional upstreams still cannot
  prevent startup.

## Which tests, builds and runtime checks were executed?

- `./gradlew build` — BUILD SUCCESSFUL, 352 tests, 0 failures, 0 skipped.
- Targeted runs per commit: `McpErrorResponseContractTest` (6),
  `McpTransportConformanceTest` (5), `IdeaSseSessionLifecycleTest` (5),
  `SseTextDecoderTest` (9), plus the full `:pyloros-auth-oauth:test` suite,
  which contains the existing root-endpoint compatibility tests.
- Mutation check on the lifecycle fix: temporarily reverting the reconnect and
  generation guards makes `duplicateDropEventsScheduleOnlyOneReconnect` and
  `lateCallbackFromSupersededAttemptDoesNotClearFreshEndpoint` fail, so both
  are genuine regression tests. The fixed version was restored afterwards.
- No runtime check against the live instance or against ChatGPT was performed.

## Result

Successful.

## Open risks

- **The root cause is not proven.** All four defects are real and reachable in
  code, but none has been correlated with an actual `Error in input stream`
  occurrence. The decisive evidence is still a failure timestamp matched
  against Pyloros logs and Caddy access logs: did the POST arrive, which
  upstream was called, did it answer, and with what status did the request
  end?
- **Deployed version is unknown.** If production runs the June jar, the
  endpoint mismatch (`/sse`, `/pyloros` vs `/`) and the dangling GET response
  are present there regardless of this branch.
- **`ToolView.named()` accepts any non-blank name**, so the `-32602` null-id
  branch in `McpRoutes` is unreachable over HTTP. The null-id envelope
  contract is therefore asserted through a test-only probe route. Worth
  deciding separately whether unknown view names should be rejected.
- **`StreamableHttpMcpUpstreamClient` is untouched and still has three
  defects:** `initialized` is set to `true` even when `initialize` fails;
  concurrent first calls can issue multiple `initialize` requests and
  overwrite the shared `sessionId`; SSE responses are read via
  `bodyAsString()`, which waits for the whole body instead of consuming the
  matching JSON-RPC message incrementally. It also does not re-initialize
  after a `404` from an expired session. Relevant only if `mcp.json` contains
  generic `streamable-http` upstreams besides IntelliJ.
- `repeatedServerSideDropsDoNotMultiplyConnections` and
  `stopCancelsPendingReconnect` pass against the old logic too — they are
  end-to-end sanity checks, not regression tests. The deterministic
  callback-replay tests are what pin the fixed behaviour.
- `IdeaSseSession` has no idle timeout on the SSE response, so a silently
  half-open connection is only noticed when the OS reports it. A heartbeat
  expectation would close that gap.
- Nothing was pushed and no release was created or modified.

## Commit hashes

- `70b43e3` Harden HTTP error handling against null values
- `546578e` Make the MCP endpoint conform to Streamable HTTP
- `7454b59` Serialize IdeaSseSession reconnects and guard against late callbacks
- `5856baf` Decode SSE streams across network buffer boundaries
