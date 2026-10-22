package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamableHttpMcpUpstreamClient implements McpUpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpUpstreamClient.class);

    private final McpUpstreamConfig config;
    private final HttpClient httpClient;
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    /**
     * The in-flight or completed handshake.
     *
     * <p>Holding the {@link Future} rather than a boolean does two things the previous flag could
     * not: concurrent first calls join one handshake instead of each issuing their own
     * {@code initialize} (which raced over {@code sessionId} and orphaned sessions), and a failed
     * handshake is cleared instead of being remembered as success.
     */
    private final AtomicReference<Future<Void>> handshake = new AtomicReference<>();

    public StreamableHttpMcpUpstreamClient(Vertx vertx, McpUpstreamConfig config) {
        this.config = config;
        this.httpClient = vertx.createHttpClient(new HttpClientOptions()
                .setConnectTimeout(config.connectTimeoutMillis()));
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return callWithSessionRecovery("tools/list", new JsonObject())
                .map(result -> {
                    JsonArray arr = result == null ? null : result.getJsonArray("tools");
                    if (arr == null) {
                        return List.of();
                    }

                    List<Map<String, Object>> tools = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        Object item = arr.getValue(i);
                        if (item instanceof JsonObject jsonObject) {
                            tools.add(new java.util.LinkedHashMap<>(jsonObject.getMap()));
                        }
                    }
                    log.info("[MCP-UPSTREAM] provider={} tools/list returned {} tools", config.providerId(), tools.size());
                    return tools;
                });
    }

    @Override
    public Future<JsonObject> callTool(String nativeToolName, JsonObject arguments) {
        JsonObject params = new JsonObject()
                .put("name", nativeToolName)
                .put("arguments", arguments == null ? new JsonObject() : arguments);
        return callWithSessionRecovery("tools/call", params);
    }

    @Override
    public Future<Void> stop() {
        try {
            httpClient.close();
        } catch (Exception ignored) {
        }
        return Future.succeededFuture();
    }

    /**
     * Performs a call, and on a {@code 404} — MCP's signal that the session no longer exists —
     * discards the session, re-runs the handshake and retries exactly once. Without this a single
     * upstream restart or session timeout left every later call failing against a stale
     * {@code Mcp-Session-Id} until Pyloros itself was restarted.
     */
    private Future<JsonObject> callWithSessionRecovery(String method, JsonObject params) {
        return ensureInitialized()
                .compose(ignored -> sendRpc(method, params))
                .recover(error -> {
                    if (!isSessionGone(error)) {
                        return Future.failedFuture(error);
                    }
                    log.info("[MCP-UPSTREAM] provider={} session expired, re-initializing", config.providerId());
                    invalidateSession();
                    // Exactly one retry: a persistently missing endpoint must fail, not loop.
                    return ensureInitialized().compose(ignored -> sendRpc(method, params));
                });
    }

    private static boolean isSessionGone(Throwable error) {
        return error instanceof McpUpstreamHttpException httpError && httpError.isSessionGone();
    }

    private void invalidateSession() {
        sessionId.set(null);
        handshake.set(null);
    }

    private Future<Void> ensureInitialized() {
        Future<Void> existing = handshake.get();
        if (existing != null) {
            return existing;
        }

        Promise<Void> promise = Promise.promise();
        if (!handshake.compareAndSet(null, promise.future())) {
            // Another caller won the race; join its handshake.
            Future<Void> winner = handshake.get();
            return winner == null ? ensureInitialized() : winner;
        }

        log.info("[MCP-UPSTREAM] provider={} connecting url={}", config.providerId(), config.url());

        JsonObject initParams = new JsonObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", new JsonObject())
                .put("clientInfo", new JsonObject().put("name", "pyloros").put("version", "0.1.0"));

        sendRpc("initialize", initParams)
                .onSuccess(ignored -> {
                    sendNotification("notifications/initialized");
                    log.info("[MCP-UPSTREAM] provider={} initialize succeeded", config.providerId());
                    promise.complete();
                })
                .onFailure(error -> {
                    log.warn("[MCP-UPSTREAM] provider={} initialize failed: {}",
                            config.providerId(), error.getMessage());
                    // Clear first, so the next call retries the handshake instead of inheriting
                    // this failure forever.
                    handshake.set(null);
                    promise.fail(error);
                });

        return promise.future();
    }

    private Future<JsonObject> sendRpc(String method, JsonObject params) {
        String id = String.valueOf(idGen.getAndIncrement());
        JsonObject body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params == null ? new JsonObject() : params);

        Promise<JsonObject> promise = Promise.promise();

        httpClient.request(requestOptions(config.responseTimeoutMillis()))
                .compose(request -> {
                    request.putHeader("Content-Type", "application/json");
                    request.putHeader("Accept", "application/json, text/event-stream");
                    config.headers().forEach(request::putHeader);
                    String sid = sessionId.get();
                    if (sid != null && !sid.isBlank()) {
                        request.putHeader("Mcp-Session-Id", sid);
                    }
                    return request.send(Buffer.buffer(body.encode()));
                })
                .onSuccess(response -> handleResponse(response, id, method, promise))
                .onFailure(promise::tryFail);

        return promise.future();
    }

    private void handleResponse(HttpClientResponse response, String id, String method, Promise<JsonObject> promise) {
        String returnedSid = response.getHeader("Mcp-Session-Id");
        if (returnedSid != null && !returnedSid.isBlank()) {
            sessionId.set(returnedSid);
        }

        int status = response.statusCode();
        if (status >= 400) {
            response.body().onComplete(result -> promise.tryFail(new McpUpstreamHttpException(
                    status,
                    method,
                    result.succeeded() && result.result() != null
                            ? result.result().toString(StandardCharsets.UTF_8)
                            : null)));
            return;
        }

        String contentType = response.getHeader("Content-Type");
        if (contentType != null && contentType.contains("text/event-stream")) {
            consumeSseResponse(response, id, method, promise);
            return;
        }

        response.body()
                .onSuccess(buffer -> completeFromJsonBody(buffer, promise))
                .onFailure(promise::tryFail);
    }

    private void completeFromJsonBody(Buffer buffer, Promise<JsonObject> promise) {
        String bodyStr = buffer == null ? null : buffer.toString(StandardCharsets.UTF_8);
        if (bodyStr == null || bodyStr.isBlank()) {
            promise.tryComplete(new JsonObject());
            return;
        }
        try {
            JsonObject json = new JsonObject(bodyStr);
            completeFromMessage(json, promise);
        } catch (Exception exception) {
            promise.tryFail(exception);
        }
    }

    /**
     * Consumes an SSE response incrementally and completes as soon as the message matching this
     * request arrives.
     *
     * <p>The previous implementation read the stream with {@code bodyAsString()}, which only
     * returns once the whole body has arrived. A server that legitimately keeps the stream open —
     * to push notifications alongside the response — therefore stalled the call until the response
     * timeout fired, even though the answer had already been delivered.
     */
    private void consumeSseResponse(HttpClientResponse response, String id, String method, Promise<JsonObject> promise) {
        SseTextDecoder decoder = new SseTextDecoder();
        StringBuilder pending = new StringBuilder();

        response.handler(chunk -> {
            if (promise.future().isComplete()) {
                return;
            }
            pending.append(decoder.decode(chunk));

            String remaining = pending.toString();
            int separator;
            while ((separator = remaining.indexOf("\n\n")) >= 0) {
                String block = remaining.substring(0, separator);
                remaining = remaining.substring(separator + 2);
                if (completeFromEventBlock(block, id, promise)) {
                    pending.setLength(0);
                    stopReading(response);
                    return;
                }
            }
            pending.setLength(0);
            pending.append(remaining);
        });

        response.exceptionHandler(promise::tryFail);
        response.endHandler(ignored -> promise.tryFail(new IllegalStateException(
                "MCP SSE stream for method=" + method + " ended without a response for id " + id)));
    }

    /**
     * @return true when the block carried the response for {@code id} and the promise was settled.
     */
    private boolean completeFromEventBlock(String block, String id, Promise<JsonObject> promise) {
        StringBuilder data = new StringBuilder();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                data.append(trimmed.substring("data:".length()).trim());
            }
        }
        if (data.length() == 0) {
            return false;
        }

        JsonObject json;
        try {
            json = new JsonObject(data.toString());
        } catch (Exception exception) {
            log.debug("[MCP-UPSTREAM] provider={} ignoring unparseable SSE data", config.providerId());
            return false;
        }

        String messageId = json.getValue("id") == null ? null : String.valueOf(json.getValue("id"));
        if (!id.equals(messageId)) {
            // A notification or another request's response sharing the stream.
            return false;
        }

        completeFromMessage(json, promise);
        return true;
    }

    private void completeFromMessage(JsonObject json, Promise<JsonObject> promise) {
        if (json.containsKey("result")) {
            promise.tryComplete(json.getJsonObject("result", new JsonObject()));
            return;
        }
        if (json.containsKey("error")) {
            promise.tryFail(new IllegalStateException("MCP error: " + json.getJsonObject("error").encode()));
            return;
        }
        promise.tryComplete(json);
    }

    /** Releases a stream the upstream would otherwise keep open after we have our answer. */
    private void stopReading(HttpClientResponse response) {
        try {
            response.request().reset();
        } catch (Exception exception) {
            log.debug("[MCP-UPSTREAM] provider={} failed to release SSE stream: {}",
                    config.providerId(), exception.getMessage());
        }
    }

    private void sendNotification(String method) {
        JsonObject body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("params", new JsonObject());

        httpClient.request(requestOptions(config.connectTimeoutMillis()))
                .compose(request -> {
                    request.putHeader("Content-Type", "application/json");
                    config.headers().forEach(request::putHeader);
                    String sid = sessionId.get();
                    if (sid != null && !sid.isBlank()) {
                        request.putHeader("Mcp-Session-Id", sid);
                    }
                    return request.send(Buffer.buffer(body.encode()));
                })
                .onFailure(error -> log.debug("[MCP-UPSTREAM] provider={} notification {} failed: {}",
                        config.providerId(), method, error.getMessage()));
    }

    private RequestOptions requestOptions(long timeoutMillis) {
        String path = config.url().getPath() == null || config.url().getPath().isBlank()
                ? "/"
                : config.url().getPath();
        boolean ssl = "https".equalsIgnoreCase(config.url().getScheme());
        int port = config.url().getPort() < 0 ? defaultPort(config.url().getScheme()) : config.url().getPort();

        return new RequestOptions()
                .setMethod(HttpMethod.POST)
                .setHost(config.url().getHost())
                .setPort(port)
                .setURI(path)
                .setSsl(ssl)
                .setConnectTimeout(config.connectTimeoutMillis())
                // Bounds how long we wait for data, not the total lifetime of a stream we keep
                // reading: once the matching message arrives the response is released.
                .setIdleTimeout(Math.max(1000, timeoutMillis));
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
