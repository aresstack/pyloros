package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * A controllable local MCP upstream for integration tests.
 *
 * <p>Every request is recorded, and the test supplies the response behaviour, so backend faults —
 * a connection dropped mid-response, a chunked SSE stream that is abandoned, a 404 for an expired
 * session — are reproduced deterministically instead of being waited for.
 */
final class McpUpstreamTestServer implements AutoCloseable {

    /** A request as the upstream saw it. */
    record Recorded(String method, JsonObject body, String sessionId, String accept) {

        String rpcMethod() {
            return body == null ? null : body.getString("method");
        }

        String rpcId() {
            return body == null || body.getValue("id") == null ? null : String.valueOf(body.getValue("id"));
        }
    }

    private final Vertx vertx;
    private final HttpServer server;
    private final List<Recorded> recorded;

    private McpUpstreamTestServer(Vertx vertx, HttpServer server, List<Recorded> recorded) {
        this.vertx = vertx;
        this.server = server;
        this.recorded = recorded;
    }

    /**
     * @param handler invoked per request with the recorded request and the raw exchange. The
     *                handler owns the response entirely, including whether it is ever ended.
     */
    static McpUpstreamTestServer start(Vertx vertx, BiConsumer<Recorded, HttpServerRequest> handler) throws Exception {
        List<Recorded> recorded = new CopyOnWriteArrayList<>();
        HttpServer server = vertx.createHttpServer().requestHandler(request -> request.bodyHandler(body -> {
            JsonObject json;
            try {
                json = body.length() == 0 ? null : new JsonObject(body);
            } catch (Exception ex) {
                json = null;
            }
            Recorded entry = new Recorded(
                    request.method().name(),
                    json,
                    request.getHeader("Mcp-Session-Id"),
                    request.getHeader("Accept")
            );
            recorded.add(entry);
            handler.accept(entry, request);
        })).listen(0).toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        return new McpUpstreamTestServer(vertx, server, recorded);
    }

    List<Recorded> requests() {
        return recorded;
    }

    long countRpc(String rpcMethod) {
        return requests().stream().filter(entry -> rpcMethod.equals(entry.rpcMethod())).count();
    }

    int port() {
        return server.actualPort();
    }

    McpUpstreamConfig config(String providerId, int responseTimeoutMillis) {
        return new McpUpstreamConfig(
                providerId,
                "streamable-http",
                java.net.URI.create("http://127.0.0.1:" + port() + "/mcp"),
                java.util.Map.of(),
                1000,
                responseTimeoutMillis,
                "test"
        );
    }

    // ---- response helpers -------------------------------------------------

    /** Answers with a normal JSON-RPC result. */
    static void respondJsonRpcResult(HttpServerRequest request, String id, JsonObject result, String sessionId) {
        HttpServerResponse response = request.response()
                .putHeader("Content-Type", "application/json");
        if (sessionId != null) {
            response.putHeader("Mcp-Session-Id", sessionId);
        }
        response.end(new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", result)
                .encode());
    }

    /** Begins an SSE response but never ends it, as a server holding the stream open may do. */
    static HttpServerResponse beginSse(HttpServerRequest request, String sessionId) {
        HttpServerResponse response = request.response()
                .setChunked(true)
                .putHeader("Content-Type", "text/event-stream");
        if (sessionId != null) {
            response.putHeader("Mcp-Session-Id", sessionId);
        }
        return response;
    }

    static String sseFrame(String id, JsonObject result) {
        return "event: message\ndata: " + new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", result)
                .encode() + "\n\n";
    }

    /** Writes raw bytes, so a test can split a multi-byte character across chunks. */
    static void writeBytes(HttpServerResponse response, byte[] bytes) {
        response.write(Buffer.buffer(bytes));
    }

    /** Kills the TCP connection, modelling a backend that dies mid-response. */
    static void dropConnection(HttpServerRequest request) {
        request.connection().close();
    }

    @Override
    public void close() throws Exception {
        Future<Void> closed = server.close();
        closed.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    Vertx vertx() {
        return vertx;
    }
}
