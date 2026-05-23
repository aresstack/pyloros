package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamableHttpMcpUpstreamClient implements McpUpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpUpstreamClient.class);

    private final McpUpstreamConfig config;
    private final WebClient webClient;
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    public StreamableHttpMcpUpstreamClient(Vertx vertx, McpUpstreamConfig config) {
        this.config = config;

        boolean ssl = "https".equalsIgnoreCase(config.url().getScheme());
        WebClientOptions options = new WebClientOptions()
                .setSsl(ssl)
                .setConnectTimeout(config.connectTimeoutMillis());
        this.webClient = WebClient.create(vertx, options);
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return ensureInitialized()
                .compose(ignored -> sendRpc("tools/list", new JsonObject()))
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
                    log.info("[MCP-UPSTREAM] provider={} tools/list returned {} tools", config.providerId(), tools.size());
                    return tools;
                });
    }

    @Override
    public Future<JsonObject> callTool(String nativeToolName, JsonObject arguments) {
        return ensureInitialized()
                .compose(ignored -> {
                    JsonObject params = new JsonObject()
                            .put("name", nativeToolName)
                            .put("arguments", arguments == null ? new JsonObject() : arguments);
                    return sendRpc("tools/call", params);
                });
    }

    @Override
    public Future<Void> stop() {
        try {
            webClient.close();
        } catch (Exception ignored) {
        }
        return Future.succeededFuture();
    }

    private Future<Void> ensureInitialized() {
        if (initialized.get()) {
            return Future.succeededFuture();
        }

        log.info("[MCP-UPSTREAM] provider={} connecting url={}", config.providerId(), config.url());

        JsonObject initParams = new JsonObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", new JsonObject())
                .put("clientInfo", new JsonObject().put("name", "pyloros").put("version", "0.1.0"));

        return sendRpc("initialize", initParams)
                .compose(ignored -> {
                    initialized.set(true);
                    sendNotification("notifications/initialized");
                    log.info("[MCP-UPSTREAM] provider={} initialize succeeded", config.providerId());
                    return Future.<Void>succeededFuture();
                })
                .recover(err -> {
                    log.warn("[MCP-UPSTREAM] provider={} initialize failed: {}", config.providerId(), err.getMessage());
                    initialized.set(true);
                    return Future.<Void>succeededFuture();
                });
    }

    private Future<JsonObject> sendRpc(String method, JsonObject params) {
        String id = String.valueOf(idGen.getAndIncrement());
        JsonObject body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params == null ? new JsonObject() : params);

        Promise<JsonObject> promise = Promise.promise();

        String host = config.url().getHost();
        int port = config.url().getPort() < 0 ? defaultPort(config.url().getScheme()) : config.url().getPort();
        String path = config.url().getPath() == null || config.url().getPath().isBlank() ? "/" : config.url().getPath();
        boolean ssl = "https".equalsIgnoreCase(config.url().getScheme());

        var req = webClient.post(port, host, path)
                .ssl(ssl)
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json, text/event-stream")
                .timeout(config.responseTimeoutMillis());

        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            req = req.putHeader(header.getKey(), header.getValue());
        }

        String sid = sessionId.get();
        if (sid != null && !sid.isBlank()) {
            req = req.putHeader("Mcp-Session-Id", sid);
        }

        req.sendBuffer(Buffer.buffer(body.encode()))
                .onSuccess(response -> {
                    String returnedSid = response.getHeader("Mcp-Session-Id");
                    if (returnedSid != null && !returnedSid.isBlank()) {
                        sessionId.set(returnedSid);
                    }

                    if (response.statusCode() >= 400) {
                        promise.fail("MCP HTTP error " + response.statusCode() + " for method=" + method);
                        return;
                    }

                    String responseContentType = response.getHeader("Content-Type");
                    String bodyStr;
                    try {
                        bodyStr = response.bodyAsString();
                    } catch (Exception ex) {
                        promise.fail(ex);
                        return;
                    }

                    if (bodyStr == null || bodyStr.isBlank()) {
                        promise.complete(new JsonObject());
                        return;
                    }

                    try {
                        if (responseContentType != null && responseContentType.contains("text/event-stream")) {
                            promise.complete(parseSseBody(bodyStr, id));
                            return;
                        }

                        JsonObject json = new JsonObject(bodyStr);
                        if (json.containsKey("result")) {
                            promise.complete(json.getJsonObject("result"));
                        } else if (json.containsKey("error")) {
                            promise.fail("MCP error: " + json.getJsonObject("error").encode());
                        } else {
                            promise.complete(json);
                        }
                    } catch (Exception ex) {
                        promise.fail(ex);
                    }
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    private void sendNotification(String method) {
        String host = config.url().getHost();
        int port = config.url().getPort() < 0 ? defaultPort(config.url().getScheme()) : config.url().getPort();
        String path = config.url().getPath() == null || config.url().getPath().isBlank() ? "/" : config.url().getPath();
        boolean ssl = "https".equalsIgnoreCase(config.url().getScheme());

        JsonObject body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("params", new JsonObject());

        var req = webClient.post(port, host, path)
                .ssl(ssl)
                .putHeader("Content-Type", "application/json")
                .timeout(config.connectTimeoutMillis());

        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            req = req.putHeader(header.getKey(), header.getValue());
        }

        String sid = sessionId.get();
        if (sid != null && !sid.isBlank()) {
            req = req.putHeader("Mcp-Session-Id", sid);
        }

        req.sendBuffer(Buffer.buffer(body.encode()))
                .onFailure(err -> log.debug("[MCP-UPSTREAM] provider={} notification {} failed: {}",
                        config.providerId(), method, err.getMessage()));
    }

    private static JsonObject parseSseBody(String body, String requestId) {
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }

            String data = trimmed.substring("data:".length()).trim();
            if (data.isBlank()) {
                continue;
            }

            JsonObject json = new JsonObject(data);
            String jsonId = json.getValue("id") == null ? null : String.valueOf(json.getValue("id"));
            if (!requestId.equals(jsonId)) {
                continue;
            }

            if (json.containsKey("result")) {
                return json.getJsonObject("result");
            }
            if (json.containsKey("error")) {
                throw new IllegalStateException("MCP error: " + json.getJsonObject("error").encode());
            }
        }

        return new JsonObject();
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
