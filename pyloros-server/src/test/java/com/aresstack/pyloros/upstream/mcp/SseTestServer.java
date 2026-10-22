package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A controllable SSE upstream for lifecycle tests.
 *
 * <p>Models the shape of the IntelliJ MCP SSE endpoint: a long-lived {@code GET} that first
 * announces a POST endpoint via an {@code endpoint} event and then stays open, plus a POST endpoint
 * that accepts JSON-RPC requests. Note the real IDE sends CRLF line endings and emits {@code data:}
 * before {@code event:}; both are reproduced here so the parsers are exercised as in production.
 *
 * <p>Faults are triggered by the test, never waited for: connections can be dropped abruptly,
 * ended cleanly, or rejected with a status for a configurable number of attempts.
 */
final class SseTestServer implements AutoCloseable {

    static final String ENDPOINT_PATH = "/message?sessionId=test-session";

    private HttpServer server;
    private final List<HttpServerResponse> liveStreams = new CopyOnWriteArrayList<>();
    private final List<io.vertx.core.http.HttpConnection> liveConnections = new CopyOnWriteArrayList<>();
    private final AtomicInteger sseConnectionCount = new AtomicInteger();
    private final AtomicInteger postCount = new AtomicInteger();

    /** Number of upcoming SSE attempts to reject, and the status to reject them with. */
    private final AtomicInteger attemptsToReject = new AtomicInteger();
    private volatile int rejectStatus = 500;

    /** When false, a connection is accepted but no endpoint event is announced. */
    private volatile boolean announceEndpoint = true;

    private SseTestServer() {
    }

    static SseTestServer start(Vertx vertx) throws Exception {
        SseTestServer instance = new SseTestServer();
        instance.server = vertx.createHttpServer()
                .requestHandler(instance::handle)
                .listen(0)
                .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        return instance;
    }

    private void handle(io.vertx.core.http.HttpServerRequest request) {
        if (request.method() == io.vertx.core.http.HttpMethod.POST) {
            postCount.incrementAndGet();
            request.bodyHandler(body -> request.response()
                    .setStatusCode(202)
                    .end());
            return;
        }

        sseConnectionCount.incrementAndGet();

        int reject = attemptsToReject.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0);
        if (reject > 0) {
            request.response().setStatusCode(rejectStatus).end("rejected");
            return;
        }

        HttpServerResponse response = request.response()
                .setChunked(true)
                .putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache");

        liveStreams.add(response);
        liveConnections.add(request.connection());

        if (announceEndpoint) {
            // CRLF and data-before-event, exactly as the IntelliJ MCP plugin emits them.
            response.write("data: " + ENDPOINT_PATH + "\r\nevent: endpoint\r\n\r\n");
        }
    }

    // ---- control surface --------------------------------------------------

    /** Kills the TCP connections of all live streams: an abrupt drop, not a clean end. */
    void dropLiveConnections() {
        for (io.vertx.core.http.HttpConnection connection : List.copyOf(liveConnections)) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
        liveConnections.clear();
        liveStreams.clear();
    }

    /** Ends all live streams cleanly, so the client sees an end rather than an error. */
    void endLiveStreams() {
        for (HttpServerResponse response : List.copyOf(liveStreams)) {
            try {
                response.end();
            } catch (Exception ignored) {
            }
        }
        liveStreams.clear();
        liveConnections.clear();
    }

    void rejectNextAttempts(int count, int status) {
        rejectStatus = status;
        attemptsToReject.set(count);
    }

    void setAnnounceEndpoint(boolean announce) {
        this.announceEndpoint = announce;
    }

    /** Pushes a JSON-RPC message to every live stream. */
    void sendMessage(JsonObject message) {
        for (HttpServerResponse response : List.copyOf(liveStreams)) {
            response.write("data: " + message.encode() + "\r\nevent: message\r\n\r\n");
        }
    }

    int sseConnectionCount() {
        return sseConnectionCount.get();
    }

    int liveStreamCount() {
        return liveStreams.size();
    }

    int port() {
        return server.actualPort();
    }

    McpUpstreamConfig config(String providerId, int connectTimeoutMillis, int responseTimeoutMillis) {
        return new McpUpstreamConfig(
                providerId,
                "sse",
                URI.create("http://127.0.0.1:" + port() + "/sse"),
                Map.of(),
                connectTimeoutMillis,
                responseTimeoutMillis,
                "test"
        );
    }

    @Override
    public void close() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
