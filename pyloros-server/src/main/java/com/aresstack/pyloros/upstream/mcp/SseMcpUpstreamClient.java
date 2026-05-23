package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SseMcpUpstreamClient implements McpUpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(SseMcpUpstreamClient.class);

    private final Vertx vertx;
    private final McpUpstreamConfig config;
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final Map<String, Promise<JsonObject>> pendingResponses = new ConcurrentHashMap<>();

    private final HttpClient sseClient;
    private final WebClient rpcClient;

    private volatile String discoveredEndpoint;
    private volatile boolean closed;

    public SseMcpUpstreamClient(Vertx vertx, McpUpstreamConfig config) {
        this.vertx = vertx;
        this.config = config;

        boolean ssl = "https".equalsIgnoreCase(config.url().getScheme());
        this.sseClient = vertx.createHttpClient();
        this.rpcClient = WebClient.create(vertx, new WebClientOptions().setSsl(ssl).setConnectTimeout(config.connectTimeoutMillis()));
    }

    @Override
    public Future<Void> start() {
        connectSse();
        return Future.succeededFuture();
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return sendRpc("tools/list", new JsonObject())
                .map(result -> {
                    JsonArray arr = result.getJsonArray("tools");
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
                    return tools;
                });
    }

    @Override
    public Future<JsonObject> callTool(String nativeToolName, JsonObject arguments) {
        JsonObject params = new JsonObject()
                .put("name", nativeToolName)
                .put("arguments", arguments == null ? new JsonObject() : arguments);
        return sendRpc("tools/call", params);
    }

    @Override
    public Future<Void> stop() {
        closed = true;
        try {
            sseClient.close();
        } catch (Exception ignored) {
        }
        try {
            rpcClient.close();
        } catch (Exception ignored) {
        }
        failPending("SSE upstream stopped");
        return Future.succeededFuture();
    }

    private Future<JsonObject> sendRpc(String method, JsonObject params) {
        String endpoint = discoveredEndpoint;
        if (endpoint == null || endpoint.isBlank()) {
            return Future.failedFuture("No SSE endpoint discovered for provider " + config.providerId());
        }

        java.net.URI endpointUri = toAbsoluteEndpoint(endpoint);
        String id = String.valueOf(idGen.getAndIncrement());
        JsonObject body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params == null ? new JsonObject() : params);

        Promise<JsonObject> promise = Promise.promise();
        pendingResponses.put(id, promise);

        if (config.responseTimeoutMillis() > 0) {
            vertx.setTimer(config.responseTimeoutMillis(), timer -> {
                Promise<JsonObject> pending = pendingResponses.remove(id);
                if (pending != null) {
                    pending.tryFail("Timeout waiting for SSE response id=" + id);
                }
            });
        }

        var req = rpcClient.request(HttpMethod.POST, endpointUri.getPort() < 0 ? defaultPort(endpointUri.getScheme()) : endpointUri.getPort(), endpointUri.getHost(), endpointUri.getPath())
                .ssl("https".equalsIgnoreCase(endpointUri.getScheme()))
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .timeout(config.responseTimeoutMillis());

        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            req = req.putHeader(header.getKey(), header.getValue());
        }

        req.sendBuffer(Buffer.buffer(body.encode()))
                .onSuccess(response -> {
                    try {
                        String syncBody = response.bodyAsString();
                        if (syncBody != null && !syncBody.isBlank()) {
                            JsonObject json = new JsonObject(syncBody);
                            Promise<JsonObject> pending = pendingResponses.remove(id);
                            if (pending != null) {
                                if (json.containsKey("result")) {
                                    pending.tryComplete(json.getJsonObject("result"));
                                } else if (json.containsKey("error")) {
                                    pending.tryFail("MCP error: " + json.getJsonObject("error").encode());
                                } else {
                                    pending.tryComplete(json);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // asynchronous SSE message path will still complete the promise
                    }
                })
                .onFailure(err -> {
                    Promise<JsonObject> pending = pendingResponses.remove(id);
                    if (pending != null) {
                        pending.tryFail(err);
                    }
                });

        return promise.future();
    }

    private void connectSse() {
        if (closed) {
            return;
        }

        int port = config.url().getPort() < 0 ? defaultPort(config.url().getScheme()) : config.url().getPort();
        String path = config.url().getPath() == null || config.url().getPath().isBlank() ? "/" : config.url().getPath();

        sseClient.request(HttpMethod.GET, port, config.url().getHost(), path)
                .onSuccess(req -> {
                    req.putHeader("Accept", "text/event-stream");
                    for (Map.Entry<String, String> header : config.headers().entrySet()) {
                        req.putHeader(header.getKey(), header.getValue());
                    }
                    req.send()
                            .onSuccess(this::handleSseResponse)
                            .onFailure(err -> scheduleReconnect("request-send-failure", err));
                })
                .onFailure(err -> scheduleReconnect("request-create-failure", err));
    }

    private void handleSseResponse(HttpClientResponse response) {
        if (response.statusCode() >= 400) {
            scheduleReconnect("status=" + response.statusCode(), null);
            return;
        }

        StringBuilder buffer = new StringBuilder();
        response.handler(chunk -> {
            String str = chunk.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
            buffer.append(str);
            String all = buffer.toString();
            int separator;
            while ((separator = all.indexOf("\n\n")) >= 0) {
                processEventBlock(all.substring(0, separator));
                all = all.substring(separator + 2);
            }
            buffer.setLength(0);
            buffer.append(all);
        });

        response.exceptionHandler(err -> scheduleReconnect("response-exception", err));
        response.endHandler(v -> scheduleReconnect("response-ended", null));
    }

    private void processEventBlock(String block) {
        String event = null;
        StringBuilder data = new StringBuilder();

        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("event:")) {
                event = trimmed.substring("event:".length()).trim();
            } else if (trimmed.startsWith("data:")) {
                data.append(trimmed.substring("data:".length()).trim());
            }
        }

        if ("endpoint".equals(event) && data.length() > 0) {
            discoveredEndpoint = data.toString();
            log.info("[MCP-UPSTREAM] provider={} discovered SSE endpoint={}", config.providerId(), discoveredEndpoint);
            return;
        }

        if (!"message".equals(event) || data.length() == 0) {
            return;
        }

        try {
            JsonObject message = new JsonObject(data.toString());
            if (!message.containsKey("id")) {
                return;
            }
            String id = String.valueOf(message.getValue("id"));
            Promise<JsonObject> pending = pendingResponses.remove(id);
            if (pending == null) {
                return;
            }

            if (message.containsKey("result")) {
                pending.tryComplete(message.getJsonObject("result"));
            } else if (message.containsKey("error")) {
                pending.tryFail("MCP error: " + message.getJsonObject("error").encode());
            } else {
                pending.tryComplete(message);
            }
        } catch (Exception ignored) {
        }
    }

    private java.net.URI toAbsoluteEndpoint(String endpoint) {
        java.net.URI base = config.url();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return java.net.URI.create(endpoint);
        }

        String normalizedPath = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        int port = base.getPort() < 0 ? defaultPort(base.getScheme()) : base.getPort();
        return java.net.URI.create(base.getScheme() + "://" + base.getHost() + ":" + port + normalizedPath);
    }

    private void scheduleReconnect(String reason, Throwable cause) {
        discoveredEndpoint = null;
        failPending("SSE upstream disconnected (" + reason + ")");

        if (closed) {
            return;
        }

        if (cause != null) {
            log.debug("[MCP-UPSTREAM] provider={} SSE reconnect scheduled reason={} error={}", config.providerId(), reason, cause.getMessage());
        }

        vertx.setTimer(Math.max(1000, config.connectTimeoutMillis()), timer -> connectSse());
    }

    private void failPending(String message) {
        if (pendingResponses.isEmpty()) {
            return;
        }
        IllegalStateException failure = new IllegalStateException(message);
        pendingResponses.forEach((id, promise) -> {
            Promise<JsonObject> removed = pendingResponses.remove(id);
            if (removed != null) {
                removed.tryFail(failure);
            }
        });
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
