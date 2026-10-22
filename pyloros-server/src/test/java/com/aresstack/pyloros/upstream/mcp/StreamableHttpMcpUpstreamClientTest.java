package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link StreamableHttpMcpUpstreamClient} against a controllable upstream.
 *
 * <p>These target the failure modes a flaky backend produces, all of which were unhandled:
 * initialization that fails but marks the client ready, concurrent first calls racing over the
 * session id, SSE responses read as a whole body instead of incrementally, and an expired session
 * that is never re-established.
 *
 * <p>Determinism: responses are gated on explicit {@link CompletableFuture}s and request counts,
 * never on sleeps.
 */
class StreamableHttpMcpUpstreamClientTest {

    private static final JsonObject TOOLS_RESULT = new JsonObject()
            .put("tools", new io.vertx.core.json.JsonArray()
                    .add(new JsonObject().put("name", "echo").put("description", "echo")));

    private Vertx vertx;
    private McpUpstreamTestServer upstream;
    private StreamableHttpMcpUpstreamClient client;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            await(client.stop());
        }
        if (upstream != null) {
            upstream.close();
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    // ---- initialization ---------------------------------------------------

    @Test
    void failedInitializeDoesNotMarkTheClientInitialized() throws Exception {
        AtomicInteger initializeCount = new AtomicInteger();
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                initializeCount.incrementAndGet();
                request.response().setStatusCode(500).end("{\"error\":\"nope\"}");
                return;
            }
            McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), TOOLS_RESULT, "session-a");
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        // A handshake that failed must not let the call through as if the upstream were ready.
        ExecutionException first = assertThrows(ExecutionException.class,
                () -> await(client.listTools()));
        assertTrue(first.getMessage().contains("500"), "error should name the upstream status: " + first.getMessage());

        // And the next call must retry the handshake rather than assume it succeeded.
        assertThrows(ExecutionException.class, () -> await(client.listTools()));
        assertEquals(2, initializeCount.get(), "every call must re-attempt a failed initialize");
    }

    @Test
    void concurrentFirstCallsIssueExactlyOneInitialize() throws Exception {
        CompletableFuture<Void> releaseInitialize = new CompletableFuture<>();
        AtomicInteger initializeCount = new AtomicInteger();

        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                int index = initializeCount.incrementAndGet();
                // Distinct session per initialize, so a race is visible in later requests.
                releaseInitialize.thenRun(() -> McpUpstreamTestServer.respondJsonRpcResult(
                        request, recorded.rpcId(), new JsonObject(), "session-" + index));
                return;
            }
            McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), TOOLS_RESULT, null);
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 5000));

        // All three calls are started before any initialize response is released, so they
        // deterministically observe the same uninitialized state.
        List<Future<List<Map<String, Object>>>> calls = new ArrayList<>();
        calls.add(client.listTools());
        calls.add(client.listTools());
        calls.add(client.listTools());

        releaseInitialize.complete(null);
        for (Future<List<Map<String, Object>>> call : calls) {
            await(call);
        }

        assertEquals(1, initializeCount.get(), "concurrent first calls must share one handshake");

        List<String> sessionIds = upstream.requests().stream()
                .filter(entry -> "tools/list".equals(entry.rpcMethod()))
                .map(McpUpstreamTestServer.Recorded::sessionId)
                .distinct()
                .toList();
        assertEquals(List.of("session-1"), sessionIds, "all calls must use one session id");
    }

    @Test
    void connectionDropDuringInitializeLeavesClientRetryable() throws Exception {
        AtomicInteger initializeCount = new AtomicInteger();
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                if (initializeCount.incrementAndGet() == 1) {
                    McpUpstreamTestServer.dropConnection(request);
                    return;
                }
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-b");
                return;
            }
            McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), TOOLS_RESULT, null);
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        assertThrows(ExecutionException.class, () -> await(client.listTools()));

        // The dropped handshake must not be remembered as success: the retry has to work.
        List<Map<String, Object>> tools = await(client.listTools());
        assertEquals(1, tools.size());
        assertEquals(2, initializeCount.get());
    }

    // ---- SSE handling -----------------------------------------------------

    @Test
    void sseResponseCompletesWithoutWaitingForStreamEnd() throws Exception {
        int responseTimeoutMillis = 3000;
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-c");
                return;
            }
            // The matching message arrives immediately, but the stream is deliberately held open,
            // as a server that may still push notifications is allowed to do.
            HttpServerResponse response = McpUpstreamTestServer.beginSse(request, null);
            response.write(McpUpstreamTestServer.sseFrame(recorded.rpcId(), TOOLS_RESULT));
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", responseTimeoutMillis));

        long startNanos = System.nanoTime();
        List<Map<String, Object>> tools = await(client.listTools());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertEquals(1, tools.size());
        assertTrue(elapsedMillis < responseTimeoutMillis / 2,
                "must complete on the matching message, not on stream end; took " + elapsedMillis + "ms");
    }

    @Test
    void sseResponseSplitAcrossChunksIsParsedIntact() throws Exception {
        String unicodeDescription = "Grüße, Köln — 😀";
        JsonObject result = new JsonObject().put("tools", new io.vertx.core.json.JsonArray()
                .add(new JsonObject().put("name", "echo").put("description", unicodeDescription)));

        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-d");
                return;
            }
            HttpServerResponse response = McpUpstreamTestServer.beginSse(request, null);
            byte[] frame = McpUpstreamTestServer.sseFrame(recorded.rpcId(), result).getBytes(StandardCharsets.UTF_8);
            // Byte-at-a-time, so multi-byte characters are split across network buffers.
            for (byte value : frame) {
                McpUpstreamTestServer.writeBytes(response, new byte[]{value});
            }
            response.end();
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 5000));

        List<Map<String, Object>> tools = await(client.listTools());
        assertEquals(1, tools.size());
        assertEquals(unicodeDescription, tools.get(0).get("description"));
    }

    @Test
    void sseStreamAbortedBeforeTheMatchingMessageFailsDefinitively() throws Exception {
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-e");
                return;
            }
            HttpServerResponse response = McpUpstreamTestServer.beginSse(request, null);
            response.write("event: message\ndata: {\"jsonrpc\":\"2.0\"");
            McpUpstreamTestServer.dropConnection(request);
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        // Must fail, and must fail promptly rather than hang until the timeout.
        long startNanos = System.nanoTime();
        assertThrows(ExecutionException.class, () -> await(client.listTools()));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        assertTrue(elapsedMillis < 2000, "an aborted stream must fail promptly; took " + elapsedMillis + "ms");
    }

    @Test
    void connectionDropMidResponseFailsWithoutHanging() throws Exception {
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-f");
                return;
            }
            request.response().setChunked(true).putHeader("Content-Type", "application/json");
            request.response().write("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"resu");
            McpUpstreamTestServer.dropConnection(request);
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        assertThrows(ExecutionException.class, () -> await(client.listTools()));
    }

    // ---- error statuses and session expiry --------------------------------

    @Test
    void serverErrorProducesDefinedFailure() throws Exception {
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-g");
                return;
            }
            request.response().setStatusCode(503).end("upstream unavailable");
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(client.listTools()));
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains("503"), "failure should name the status: " + failure.getMessage());
    }

    @Test
    void expiredSessionIsReinitializedAfter404() throws Exception {
        AtomicInteger initializeCount = new AtomicInteger();
        AtomicInteger listCount = new AtomicInteger();

        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                int index = initializeCount.incrementAndGet();
                McpUpstreamTestServer.respondJsonRpcResult(
                        request, recorded.rpcId(), new JsonObject(), "session-" + index);
                return;
            }
            if ("tools/list".equals(recorded.rpcMethod())) {
                // The first call succeeds; afterwards the server forgets the session once.
                if (listCount.incrementAndGet() == 2) {
                    request.response().setStatusCode(404).end("{\"error\":\"session not found\"}");
                    return;
                }
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), TOOLS_RESULT, null);
            }
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 3000));

        assertEquals(1, await(client.listTools()).size());

        // The second call hits the expired session. MCP requires starting a new session rather
        // than failing forever with the stale id.
        List<Map<String, Object>> afterExpiry = await(client.listTools());
        assertEquals(1, afterExpiry.size(), "client must recover from an expired session");
        assertEquals(2, initializeCount.get(), "a 404 must trigger exactly one re-initialize");

        List<String> sessionsUsed = upstream.requests().stream()
                .filter(entry -> "tools/list".equals(entry.rpcMethod()))
                .map(McpUpstreamTestServer.Recorded::sessionId)
                .toList();
        assertEquals("session-1", sessionsUsed.get(0));
        assertEquals("session-1", sessionsUsed.get(1));
        assertEquals("session-2", sessionsUsed.get(2), "the retry must carry the new session id");
    }

    @Test
    void repeated404DoesNotLoopForever() throws Exception {
        AtomicInteger initializeCount = new AtomicInteger();
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                initializeCount.incrementAndGet();
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-x");
                return;
            }
            request.response().setStatusCode(404).end("{\"error\":\"gone\"}");
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        assertThrows(ExecutionException.class, () -> await(client.listTools()));
        // Exactly one retry: the initial handshake plus one re-initialize.
        assertEquals(2, initializeCount.get(), "a persistent 404 must not retry endlessly");
        assertTrue(upstream.countRpc("tools/list") <= 2, "at most one retry of the call itself");
    }

    @Test
    void callsAfterAFailedCallStillWork() throws Exception {
        AtomicInteger listCount = new AtomicInteger();
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "session-h");
                return;
            }
            // Only the first tools/list is dropped. Matching on the method matters: the client
            // also posts notifications/initialized, which must not consume the drop.
            if (!"tools/list".equals(recorded.rpcMethod())) {
                request.response().setStatusCode(202).end();
                return;
            }
            if (listCount.incrementAndGet() == 1) {
                McpUpstreamTestServer.dropConnection(request);
                return;
            }
            McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), TOOLS_RESULT, null);
        });
        client = new StreamableHttpMcpUpstreamClient(vertx, upstream.config("test", 2000));

        assertThrows(ExecutionException.class, () -> await(client.listTools()));
        assertFalse(await(client.listTools()).isEmpty(), "a transient failure must not poison the client");
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }
}
