# Report — sporadic MCP "Error in input stream"

## Status

- Repository: aresstack/pyloros
- Branch: `fix/mcp-stream-robustness-clean-20261022`, pushed to `origin`
- Draft PR: https://github.com/aresstack/pyloros/pull/109 (not merged, no release)
- Superseded: PR #108, closed; its branch left intact, central-OAuth work
  preserved on `feature/central-oauth-backend`
- Base of the MCP work: `217fe5c`

**The cause of the production error is still not proven.** This round added a
live investigation that produced an important *negative* result: the defects
found in the Streamable HTTP upstream client are real and reproducible against
a synthetic upstream, but none of them reproduces against the upstreams this
installation actually runs. They are hardening, not the explanation.

## 1. Which concrete faults were reproduced

Reproduced deterministically against a controllable local upstream
(`McpUpstreamTestServer`), each demonstrated to fail before the fix:

| Fault | Reproduced | Effect |
| --- | --- | --- |
| `initialize` fails, client marks itself initialized | yes | the next call proceeds as if the upstream were ready |
| Concurrent first calls each run their own handshake | yes, 3 of 3 | sessions race over `sessionId`, all but one orphaned |
| SSE response read as a whole body | yes | call stalls for the full response timeout (production default 60 s) |
| Expired session (`404`) never re-established | yes | every later call fails against the stale `Mcp-Session-Id` |
| Duplicate drop events schedule two reconnects | yes | connection count grows per drop cycle |
| Late callback from a superseded attempt clears the endpoint | yes | upstream looks connected but has no usable endpoint |
| Multi-byte UTF-8 split across network buffers | yes | replacement characters in JSON-RPC payloads |
| `\r\n` split across buffers | yes | fabricated SSE event boundary splits one event in two |
| Null id / null message / null result in a JSON-RPC envelope | yes | `NullPointerException` in an async callback; response never ended |

## 2. Which tests detect these faults before the correction

Verified by reverting the implementation and re-running:

- `StreamableHttpMcpUpstreamClientTest` — **7 of 11 fail** against the previous
  client (failed-initialize, concurrent handshake, held-open SSE, 404
  recovery, drop-during-initialize, persistent 404, post-failure reuse).
- `McpUpstreamFailureEndToEndTest` — **2 of 4 fail** against the previous
  client, proving a 60 s upstream stall reaches the public endpoint rather
  than being contained in the upstream client.
- `IdeaSseSessionLifecycleTest` — `duplicateDropEventsScheduleOnlyOneReconnect`
  and `lateCallbackFromSupersededAttemptDoesNotClearFreshEndpoint` fail with
  the guards removed.
- `SseTextDecoderTest` — the split-offset and byte-at-a-time cases fail against
  per-buffer decoding.
- `McpErrorResponseContractTest` — the async messageless-failure case now also
  bounds elapsed time, so a response produced by the client timing out no
  longer satisfies it.

Tests that do **not** detect a regression, and are sanity checks only:
`repeatedServerSideDropsDoNotMultiplyConnections`, `stopCancelsPendingReconnect`.

## 3. Which causes are confirmed and which are excluded

### Verified live against the running instance

The deployed baseline was established by endpoint fingerprint, not assumption.
The public instance ran as PID 83660 on port 8081 (`java -jar .\pyloros.jar`,
started 2026-10-21 23:46). Probed over loopback: `/` → 401 `missing_token`,
`/sse` → 404, `/pyloros` → 404, `/actuator/health` → 200, `/health` → 404.
That is exactly the `217fe5c` layout, so **the live instance matched current
main**. The stale 17 June jar in `build/` is not what was running; that earlier
claim is withdrawn.

With the user's server stopped, a fixed build was run on port 18188 and
confirmed:

- `GET /` → `405 Method Not Allowed`, `allow: POST`, `application/json`, no SSE frame.
- Notification → `202`, `content-length: 0`, no `Content-Type`.
- `initialize` → well-formed result.
- `tools/list` → 200, 259 tools from 9 providers in 0.6–1.5 s.

### Excluded by measurement

The configured upstreams were probed directly. `mcp-steroid`,
`intellij-index` and `debugger-mcp-server` answer **`application/json`** for
both `initialize` and `tools/call`, in milliseconds, and send **no
`Mcp-Session-Id` at all** — they are sessionless. `mcp-code-intel` (port
17532) is down and is correctly recovered to an empty tool list.

A build with the *old* upstream client was then run against the same real
upstreams: `tools/list` returned the identical 259 tools in 1.5 s. So:

- The 60 s SSE stall **does not occur** with these upstreams — none holds a
  `text/event-stream` response open, including GitHub Copilot's remote MCP.
- The `404` session-recovery path **does not apply** — these upstreams carry no
  session at all.
- The concurrent-handshake race is possible but its only observed consequence
  (an orphaned session) is inert against sessionless upstreams.

**None of the four Streamable HTTP defects is therefore confirmed as the
production cause.**

### Still open as candidates

- **The null-value crash in `HttpJson`** is a genuine latent defect, but no
  concrete production exception with a `null` message was found. `getMessage()`
  is non-null for every failure `IdeaSseSession` produces, and modern JDKs give
  NPEs a message. The null-id branch is unreachable over HTTP because
  `ToolView.named()` accepts any non-blank name. Reachability is real but
  unproven.
- **The IDEA SSE path was the best fit for the symptom, and round 3 ruled it
  out.** See section 5b: a dropped IDE connection yields a prompt 200 with an
  empty tool list, never an error or a stall.

## 4. Remaining risks

- The production trigger is unidentified. A failure timestamp correlated with
  Pyloros and Caddy access logs is still the decisive missing evidence.
- Neither SSE client has idle-timeout based half-open detection. This is a
  deliberate decision, not an omission — see section 5b. A quiet SSE stream is
  legitimate and the transport defines no mandatory keepalive, so a blanket
  idle timeout would kill healthy connections.
- `SseMcpUpstreamClient` now has the same generation guards as
  `IdeaSseSession`. Note it currently serves no configured upstream:
  `intellij` is routed to `IdeaMcpClient` and everything else uses
  streamable-http.
- IntelliJ emits `notifications/tools/list_changed` continuously, not only at
  startup. The 1.5 s debounce collapses them, but the catalog is refreshed
  repeatedly for as long as the IDE keeps emitting.
- `tools/list` is roughly 412 KB for 259 tools across 9 providers. Not
  investigated, and not a known fault — but it is the largest untested
  variable at the public endpoint and the tool count fluctuates with IDE
  state.
- Locally, a second Pyloros instance (`pyloros-app/build/libs/pyloros.jar` on
  127.0.0.1:18099) was running alongside the public one and was holding the
  build directory. Both are stopped now; **nothing was restarted.**

## 5. Changes committed and pushed

On `fix/mcp-stream-robustness-20261022`:

- `70b43e3` Harden HTTP error handling against null values
- `546578e` Make the MCP endpoint conform to Streamable HTTP
- `7454b59` Serialize IdeaSseSession reconnects and guard against late callbacks
- `5856baf` Decode SSE streams across network buffer boundaries
- `93dda7d` Report findings and fixes
- `2adbff1` Make the Streamable HTTP upstream client survive a flaky backend
- `84f0e52` Bound how long a broken upstream may delay the public MCP endpoint
- `2530442` Give SseMcpUpstreamClient the same connection lifecycle guards
- `6638be0` Trace the IDEA SSE path to the public endpoint and rule it out

(Commit hashes differ from PR #108's by the cherry-pick onto the clean branch.)

Files added this round:

- `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/McpUpstreamHttpException.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/upstream/mcp/McpUpstreamTestServer.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/upstream/mcp/StreamableHttpMcpUpstreamClientTest.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/upstream/mcp/McpUpstreamFailureEndToEndTest.java`

Changed: `StreamableHttpMcpUpstreamClient.java`, `McpErrorResponseContractTest.java`.

The webserver and its TLS configuration were not touched. Nothing merged, no
release, no production configuration changed.

## 5b. IDEA SSE path investigation (round 3)

### Corrections to this report

Two earlier statements were wrong and are withdrawn:

1. "`IdeaToolProvider.listTools()` does not recover, so a failure there
   propagates to the public endpoint." Its `try/catch` covers only
   *synchronous* throws, but the failure never gets that far:
   `IdeaMcpClient.listTools()` completes with `List.of()` on **every** path —
   not ready, parse failure, and its `onFailure` handler. Neither method can
   fail its future.
2. The earlier claim that the stale June jar served production traffic;
   already withdrawn in round 2.

### Which faults were reproduced

`SseMcpUpstreamClient` carried exactly the defects already fixed in
`IdeaSseSession`, because the previous round only touched its UTF-8 decoding:
an unguarded `scheduleReconnect()` reached from both the response exception
handler and the end handler, an unconditionally cleared `discoveredEndpoint`,
and a reconnect timer left armed by `stop()`. Reproduced and fixed with the
same generation-numbered attempt pattern.

### Which tests fail against the previous code

Of the seven `SseMcpUpstreamClientLifecycleTest` cases, **two** fail against
the old semantics: `duplicateDropEventsScheduleOnlyOneReconnect` and
`lateCallbackFromSupersededAttemptDoesNotClearFreshEndpoint`. The other five —
abrupt close with recovery, rejected attempt retried, stop during a pending
reconnect, quiet stream staying valid, pending request failed on disconnect —
pass either way and document behaviour rather than pinning a regression. The
same split applies to the extended `IdeaSseSessionLifecycleTest`.

### Does this explain "Error in input stream"? No.

`IdeaSseEndToEndTest` runs the production chain against a controllable SSE
server. When the IDE connection drops, the public endpoint answers **200 with
an empty tool list**, promptly, with no JSON-RPC error and no stall. A
`tools/call` while disconnected returns an `isError` result, also promptly.

A client-side broken input stream requires an unanswered or truncated
response. This path produces neither. **The IDEA SSE path is ruled out as the
cause.** Combined with round 2, where the Streamable HTTP defects were ruled
out against the actually configured upstreams, no examined Pyloros path
currently explains the symptom.

What the live instance did show: the IntelliJ plugin emits
`notifications/tools/list_changed` **continuously**, not only at startup, and
uses CRLF with `data:` before `event:`. The parsers handle that format; the
tests now use it.

### Idle timeout: deliberately not implemented

Observed read-only on the running IDE endpoint for 30 s: no comment or
heartbeat frames, only `list_changed` notifications. The MCP SSE transport
defines no mandatory keepalive, so a quiet stream is legitimate and a
data-based idle timeout would kill healthy connections — precisely the
behaviour to avoid. `quietStreamWithoutEventsStaysValid` now pins that a
silent stream stays valid in both clients.

Detection therefore stays at the transport level, where Vert.x surfaces a
closed or reset connection through the exception and end handlers; the tests
confirm an abrupt server-side close is detected and recovered. A connect
timeout is bounded, which is a different thing from an idle timeout. If
half-open detection is ever needed, the correct mechanism is an application
heartbeat agreed with the upstream, not a blanket timeout.

## 6. GitHub Actions

`Verify Root MCP` runs on `pull_request` and `workflow_dispatch` only.

On `fix/mcp-stream-robustness-20261022` it **cannot** verify this work. Since
`3a13df6` the build fails during dependency resolution:
`com.aresstack:anauthentia:0.1.0-SNAPSHOT` and `anauthentia-jwt:0.1.0-SNAPSHOT`
are required by `pyloros-auth-oauth` but are published neither to Maven Central
nor to the runner's local repository. It resolves locally only because those
artifacts are installed in the developer's `~/.m2`. This is a consequence of
the central-OAuth commit sitting on the branch, not of the MCP work.

One commit earlier, `4fe851e` (documentation only) failed on
`AcpIntegrationTest > testErrorSession()`, which had passed at `93dda7d`. That
test is flaky on the runner and unrelated to MCP.

To obtain an actual CI confirmation, `fix/mcp-stream-robustness-clean-20261022`
was created from `4fe851e` with the three new commits cherry-picked, i.e. every
MCP commit and none of the central-OAuth change. Manually dispatched run
[39769671595](https://github.com/aresstack/pyloros/actions/runs/39769671595) on
`ae5b5bf`: **BUILD SUCCESSFUL**. No pull request was opened for this branch and
PR #108 was left untouched.

## 7. Build and test result

`./gradlew build` — BUILD SUCCESSFUL, **381 tests, 0 failures, 0 skipped**
(`clean` fails only while a local instance holds `pyloros-app/build`).
New in round 2: `StreamableHttpMcpUpstreamClientTest` (11),
`McpUpstreamFailureEndToEndTest` (4). New in round 3:
`SseMcpUpstreamClientLifecycleTest` (7), `IdeaSseEndToEndTest` (4), plus three
more `IdeaSseSessionLifecycleTest` cases.
