package com.aresstack.pyloros.upstream.idea;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the POST side of the IDEA transport, which no test reached before.
 *
 * <p>IDEA MCP answers a {@code tools/call} over the SSE stream and acknowledges the POST with 202.
 * That made the POST response look irrelevant, and the code treated it as such: it stringified the
 * {@code Future<Buffer>} returned by {@code response.body()} and swallowed the resulting parse
 * error. Two consequences, both reproduced here as the first two tests: a genuinely synchronous
 * JSON-RPC body was discarded, and an HTTP error status was indistinguishable from an
 * acknowledgement — so both could only ever end in the 60 s SSE timeout.
 *
 * <p>The remaining tests pin the consequences of that: a response arriving after its timeout used to
 * fall through the id correlation without a trace, which made "the IDE never answered" and "the IDE
 * answered too late" look identical in the log; and a request settled anywhere other than the SSE
 * path kept its registration and its armed timeout timer alive for the whole window.
 */
class IdeaJsonRpcTransportTest {

    /** Short enough to let a timeout test run, long enough that a healthy call never trips it. */
    private static final int RESPONSE_TIMEOUT_MILLIS = 1000;
    private static final long FAST_FAILURE_BUDGET_MILLIS = RESPONSE_TIMEOUT_MILLIS / 2;

    /**
     * Deadline for the cleanup probes below, far enough out that the timeout timer cannot be what
     * retires the registration. With the configured 1 s timeout the timer tidies up on its own
     * within any sane wait, and the probes would pass whether or not the release path works.
     */
    private static final long CLEANUP_PROBE_TIMEOUT_MILLIS = 30_000;

    /** Long enough to lose a race against the HTTP body, short enough to stay inside the timeout. */
    private static final long SSE_ANSWER_DELAY_MILLIS = 250;

    private Vertx vertx;
    private HttpServer ideServer;
    private IdeaSseSession session;
    private IdeaJsonRpcClient client;

    private final CopyOnWriteArrayList<io.vertx.core.http.HttpServerResponse> liveStreams = new CopyOnWriteArrayList<>();
    private ListAppender<ILoggingEvent> sessionLog;
    private Logger sessionLogger;
    private ListAppender<ILoggingEvent> clientLog;
    private Logger clientLogger;

    /** How the fake IDE answers a POST: receives the parsed JSON-RPC request and the HTTP response. */
    private volatile BiConsumer<JsonObject, io.vertx.core.http.HttpServerResponse> postBehaviour;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        postBehaviour = (rpc, response) -> response.setStatusCode(202).end();

        ideServer = await(vertx.createHttpServer().requestHandler(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.POST) {
                request.bodyHandler(body -> postBehaviour.accept(new JsonObject(body), request.response()));
                return;
            }

            io.vertx.core.http.HttpServerResponse response = request.response()
                    .setChunked(true)
                    .putHeader("Content-Type", "text/event-stream");
            liveStreams.add(response);
            response.write("data: /messages/fake\r\nevent: endpoint\r\n\r\n");
        }).listen(0));

        IdeaMcpConfig config = new IdeaMcpConfig(
                URI.create("http://127.0.0.1:" + ideServer.actualPort() + "/sse"),
                Map.of(),
                1000,
                RESPONSE_TIMEOUT_MILLIS
        );

        sessionLogger = (Logger) LoggerFactory.getLogger(IdeaSseSession.class);
        sessionLog = new ListAppender<>();
        sessionLog.start();
        sessionLogger.addAppender(sessionLog);

        clientLogger = (Logger) LoggerFactory.getLogger(IdeaJsonRpcClient.class);
        clientLog = new ListAppender<>();
        clientLog.start();
        clientLogger.addAppender(clientLog);

        session = new IdeaSseSession(vertx, config);
        session.start();
        waitUntil(session::isReady);

        client = new IdeaJsonRpcClient(vertx, config, session);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sessionLogger != null && sessionLog != null) {
            sessionLogger.detachAppender(sessionLog);
            sessionLog.stop();
        }
        if (clientLogger != null && clientLog != null) {
            clientLogger.detachAppender(clientLog);
            clientLog.stop();
        }
        if (session != null) {
            await(session.stop());
        }
        if (ideServer != null) {
            await(ideServer.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    void synchronousJsonRpcBodyCompletesTheCall() throws Exception {
        // No SSE push at all: the POST body carries the whole answer.
        postBehaviour = (rpc, response) -> response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", rpc.getValue("id"))
                        .put("result", new JsonObject().put("answer", "sync"))
                        .encode());

        long startNanos = System.nanoTime();
        JsonObject result = await(client.postJsonRpc("tools/call", new JsonObject()));

        assertEquals("sync", result.getString("answer"));
        assertTrue(elapsedMillis(startNanos) < FAST_FAILURE_BUDGET_MILLIS,
                "a synchronous body must not wait for the SSE timeout; took " + elapsedMillis(startNanos) + "ms");
    }

    @Test
    void httpErrorStatusFailsTheCallInsteadOfWaitingForTheTimeout() {
        postBehaviour = (rpc, response) -> response.setStatusCode(500).end("tool blew up");

        long startNanos = System.nanoTime();
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> await(client.postJsonRpc("tools/call", new JsonObject())));

        assertTrue(failure.getCause().getMessage().contains("HTTP 500"),
                "the status has to reach the caller: " + failure.getCause().getMessage());
        assertTrue(failure.getCause().getMessage().contains("tool blew up"),
                "the error body has to reach the caller: " + failure.getCause().getMessage());
        assertTrue(elapsedMillis(startNanos) < FAST_FAILURE_BUDGET_MILLIS,
                "an HTTP error must not wait for the SSE timeout; took " + elapsedMillis(startNanos) + "ms");
    }

    @Test
    void sseResponseArrivingAfterTheTimeoutIsLoggedAsLate() throws Exception {
        // Acknowledge the POST, then answer over SSE only after the response timeout has elapsed.
        postBehaviour = (rpc, response) -> {
            response.setStatusCode(202).end();
            vertx.setTimer(RESPONSE_TIMEOUT_MILLIS * 2L, t -> pushResult(rpc.getValue("id")));
        };

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> await(client.postJsonRpc("tools/call", new JsonObject())));
        assertTrue(failure.getCause().getMessage().contains("Timeout waiting for IDEA SSE response"),
                "the caller still has to see a timeout: " + failure.getCause().getMessage());

        waitUntil(() -> logContains("arrived") && logContains("after its timeout"), 5000);
    }

    @Test
    void onTimeSseResponseStillCompletesTheCall() throws Exception {
        postBehaviour = (rpc, response) -> {
            response.setStatusCode(202).end();
            pushResult(rpc.getValue("id"));
        };

        JsonObject result = await(client.postJsonRpc("tools/call", new JsonObject()));

        assertEquals("sse", result.getString("answer"));
        assertTrue(logContains("matched pending id"), "the correlation has to be traceable in the log");
    }

    /**
     * The SSE message is not the only way a request ends, but it used to be the only way its
     * registration was retired. Each terminal outcome below settles the promise outside the SSE
     * path, and each used to leave the entry behind with its timeout timer still armed for the full
     * window — now up to an hour, since a tool's own deadline can extend it.
     *
     * <p>The armed timer itself is not observable from outside Vert.x. The registration it belongs
     * to is, and the same call retires both, so the count stands in for the timer.
     *
     * <p>Each probe passes an explicit long deadline: see {@link #CLEANUP_PROBE_TIMEOUT_MILLIS}.
     */
    @Test
    void aSynchronousResponseRetiresThePendingRegistration() throws Exception {
        postBehaviour = (rpc, response) -> response
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("id", rpc.getValue("id"))
                        .put("result", new JsonObject().put("answer", "sync"))
                        .encode());

        await(client.postJsonRpc("tools/call", new JsonObject(), CLEANUP_PROBE_TIMEOUT_MILLIS));

        waitUntil(() -> session.pendingResponseCount() == 0, FAST_FAILURE_BUDGET_MILLIS);
    }

    @Test
    void anHttpErrorStatusRetiresThePendingRegistration() throws Exception {
        postBehaviour = (rpc, response) -> response.setStatusCode(500).end("tool blew up");

        assertThrows(ExecutionException.class,
                () -> await(client.postJsonRpc("tools/call", new JsonObject(), CLEANUP_PROBE_TIMEOUT_MILLIS)));

        waitUntil(() -> session.pendingResponseCount() == 0, FAST_FAILURE_BUDGET_MILLIS);
    }

    @Test
    void aFailedPostRetiresThePendingRegistration() throws Exception {
        // The connection goes away under the POST, so the send never produces a response at all.
        postBehaviour = (rpc, response) -> response.reset();

        assertThrows(ExecutionException.class,
                () -> await(client.postJsonRpc("tools/call", new JsonObject(), CLEANUP_PROBE_TIMEOUT_MILLIS)));

        waitUntil(() -> session.pendingResponseCount() == 0, FAST_FAILURE_BUDGET_MILLIS);
    }

    @Test
    void aLargeErrorBodyIsNeitherLoggedNorReturnedInFull() {
        // A rejected call can answer with an HTML error page, and a successful one with thousands of
        // lines of terminal output that may contain credentials. Neither belongs in the log verbatim.
        String huge = "x".repeat(20_000);
        postBehaviour = (rpc, response) -> response.setStatusCode(500).end(huge);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> await(client.postJsonRpc("tools/call", new JsonObject())));

        assertFalse(failure.getCause().getMessage().contains(huge),
                "the full body must not reach the caller");
        assertTrue(failure.getCause().getMessage().contains("chars total"),
                "the truncation has to be visible, not silent: " + failure.getCause().getMessage());
        assertFalse(clientLog.list.stream().anyMatch(e -> e.getFormattedMessage().contains(huge)),
                "the full body must not reach the log");
    }

    @Test
    void aJsonRpcErrorOverSseFailsTheCallInsteadOfCompletingIt() {
        postBehaviour = (rpc, response) -> {
            response.setStatusCode(202).end();
            pushEnvelope(new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", rpc.getValue("id"))
                    .put("error", new JsonObject()
                            .put("code", -32603)
                            .put("message", "Internal error")));
        };

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> await(client.postJsonRpc("tools/call", new JsonObject())));

        assertTrue(failure.getCause().getMessage().contains("Internal error"),
                "the upstream error has to reach the caller: " + failure.getCause().getMessage());
    }

    @Test
    void aSynchronousBodyForAnotherRequestIdIsIgnored() throws Exception {
        // A well-formed envelope carrying somebody else's id, with the real answer following over
        // SSE afterwards. Accepting the first would discard the second.
        postBehaviour = (rpc, response) -> {
            response.setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("jsonrpc", "2.0")
                            .put("id", "some-other-request")
                            .put("result", new JsonObject().put("answer", "wrong-correlation"))
                            .encode());
            pushLater(rpc.getValue("id"));
        };

        JsonObject result = await(client.postJsonRpc("tools/call", new JsonObject()));

        assertEquals("sse", result.getString("answer"),
                "the answer must come from the correlated SSE response, not the mismatched body");
    }

    @Test
    void aSynchronousBodyWithoutAResponseEnvelopeIsIgnored() throws Exception {
        // What a reverse proxy or health endpoint in front of the IDE might answer with.
        postBehaviour = (rpc, response) -> {
            response.setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("status", "ok").encode());
            pushLater(rpc.getValue("id"));
        };

        JsonObject result = await(client.postJsonRpc("tools/call", new JsonObject()));

        assertEquals("sse", result.getString("answer"),
                "a body that is not a JSON-RPC response must not settle the call");
    }

    /**
     * Answers over SSE only after the HTTP body has certainly been processed.
     *
     * <p>Pushing immediately makes the two answers race, and the SSE one tends to win — which let
     * both correlation tests above pass even with the validation removed. The delay makes the
     * unvalidated body deterministically the first to settle the call.
     */
    private void pushLater(Object id) {
        vertx.setTimer(SSE_ANSWER_DELAY_MILLIS, t -> pushResult(id));
    }

    private void pushResult(Object id) {
        pushEnvelope(new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", new JsonObject().put("answer", "sse")));
    }

    private void pushEnvelope(JsonObject message) {
        for (io.vertx.core.http.HttpServerResponse stream : java.util.List.copyOf(liveStreams)) {
            try {
                stream.write("data: " + message.encode() + "\r\nevent: message\r\n\r\n");
            } catch (Exception ignored) {
            }
        }
    }

    private boolean logContains(String fragment) {
        return sessionLog.list.stream().anyMatch(event -> event.getFormattedMessage().contains(fragment));
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private void waitUntil(BooleanCondition condition) throws Exception {
        waitUntil(condition, 5000);
    }

    private void waitUntil(BooleanCondition condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met within " + timeoutMillis + "ms");
    }

    private interface BooleanCondition {
        boolean test();
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }
}
