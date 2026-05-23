package com.aresstack.pyloros.upstream.github;

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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-based MCP client for the GitHub remote MCP server.
 * <p>
 * Implements MCP Streamable HTTP transport (2025-03-26).
 * Responses may arrive as {@code application/json} or {@code text/event-stream}; both are handled.
 * The GitHub MCP token must never be logged.
 */
public final class GitHubMcpClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubMcpClient.class);

    private static final String TOOL_PREFIX = "github/";

    private final GitHubMcpConfig config;
    private final WebClient webClient;
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private final AtomicReference<List<Map<String, Object>>> cachedTools = new AtomicReference<>();

    public GitHubMcpClient(Vertx vertx, GitHubMcpConfig config) {
        this.config = config;
        WebClientOptions options = new WebClientOptions()
                .setSsl(true)
                .setConnectTimeout(config.connectTimeoutMillis());
        this.webClient = WebClient.create(vertx, options);
    }

    // ---- public API --------------------------------------------------------

    public Future<List<Map<String, Object>>> listTools() {
        if (!config.enabled() || config.token() == null || config.token().isBlank()) {
            return Future.succeededFuture(List.of());
        }

        List<Map<String, Object>> cached = cachedTools.get();
        if (cached != null) {
            return Future.succeededFuture(cached);
        }

        Promise<List<Map<String, Object>>> promise = Promise.promise();

        ensureInitialized()
                .compose(ignored -> sendRpc("tools/list", new JsonObject()))
                .onSuccess(result -> {
                    try {
                        if (result == null) {
                            promise.complete(List.of());
                            return;
                        }
                        JsonArray arr = result.getJsonArray("tools");
                        if (arr == null) {
                            promise.complete(List.of());
                            return;
                        }

                        List<Map<String, Object>> tools = new ArrayList<>();
                        for (int i = 0; i < arr.size(); i++) {
                            Object item = arr.getValue(i);
                            if (!(item instanceof JsonObject jo)) continue;

                            Map<String, Object> map = new java.util.LinkedHashMap<>(jo.getMap());
                            Object nameObj = map.get("name");
                            if (nameObj instanceof String originalName && !originalName.isBlank()) {
                                map.put("name", TOOL_PREFIX + originalName);
                            }
                            tools.add(map);
                        }

                        log.info("[MCP-UPSTREAM] provider=github tools/list returned {} tools", tools.size());
                        cachedTools.set(tools);
                        promise.complete(tools);
                    } catch (Exception ex) {
                        log.warn("[MCP-UPSTREAM] provider=github tools/list parse error: {}", ex.getMessage());
                        promise.complete(List.of());
                    }
                })
                .onFailure(err -> {
                    log.warn("[MCP-UPSTREAM] provider=github tools/list failed: {}", err.getMessage());
                    promise.complete(List.of());
                });

        return promise.future();
    }

    public Future<JsonObject> callTool(String nativeToolName, JsonObject arguments) {
        return ensureInitialized()
                .compose(ignored -> {
                    JsonObject params = new JsonObject()
                            .put("name", nativeToolName)
                            .put("arguments", arguments == null ? new JsonObject() : arguments);
                    return sendRpc("tools/call", params);
                });
    }

    public Future<Void> stop() {
        try {
            webClient.close();
        } catch (Exception ignored) {
        }
        return Future.succeededFuture();
    }

    // ---- initialization ----------------------------------------------------

    private Future<Void> ensureInitialized() {
        if (initialized.get()) {
            return Future.succeededFuture();
        }

        log.info("[MCP-UPSTREAM] provider=github connecting url={}", config.url());

        JsonObject initParams = new JsonObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", new JsonObject())
                .put("clientInfo", new JsonObject()
                        .put("name", "pyloros")
                        .put("version", "1.0"));

        return sendRpc("initialize", initParams)
                .compose(result -> {
                    initialized.set(true);
                    log.info("[MCP-UPSTREAM] provider=github initialize succeeded");
                    // Send notifications/initialized (no response expected)
                    sendNotification("notifications/initialized");
                    return Future.<Void>succeededFuture();
                })
                .recover(err -> {
                    log.warn("[MCP-UPSTREAM] provider=github initialize failed: {}", err.getMessage());
                    // Mark as initialized anyway so we still attempt tools/list
                    initialized.set(true);
                    return Future.succeededFuture();
                });
    }

    // ---- RPC helpers -------------------------------------------------------

    /**
     * Send a JSON-RPC request and return the {@code result} field.
     * Handles both {@code application/json} and {@code text/event-stream} responses.
     */
    private Future<JsonObject> sendRpc(String method, JsonObject params) {
        String id = String.valueOf(idGen.getAndIncrement());
        JsonObject body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params == null ? new JsonObject() : params);

        Promise<JsonObject> promise = Promise.promise();

        try {
            URI uri = URI.create(config.url());
            String host = uri.getHost();
            int port = uri.getPort() < 0 ? 443 : uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isBlank()) path = "/";

            var req = webClient.post(port, host, path)
                    .ssl(true)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Accept", "application/json, text/event-stream")
                    .timeout(config.responseTimeoutMillis());

            // Authorization: token must not be logged
            req = req.putHeader("Authorization", "Bearer " + config.token());

            String sid = sessionId.get();
            if (sid != null && !sid.isBlank()) {
                req = req.putHeader("Mcp-Session-Id", sid);
            }

            req.sendBuffer(Buffer.buffer(body.encode()))
                    .onSuccess(response -> {
                        // Capture session ID if returned
                        String returnedSid = response.getHeader("Mcp-Session-Id");
                        if (returnedSid != null && !returnedSid.isBlank()) {
                            sessionId.set(returnedSid);
                        }

                        if (response.statusCode() >= 400) {
                            promise.fail("GitHub MCP HTTP error " + response.statusCode()
                                    + " for method=" + method);
                            return;
                        }

                        String contentType = response.getHeader("Content-Type");
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
                            if (contentType != null && contentType.contains("text/event-stream")) {
                                JsonObject result = parseSseBody(bodyStr, id);
                                promise.complete(result);
                            } else {
                                JsonObject json = new JsonObject(bodyStr);
                                if (json.containsKey("result")) {
                                    promise.complete(json.getJsonObject("result"));
                                } else if (json.containsKey("error")) {
                                    promise.fail("GitHub MCP error: " + json.getJsonObject("error").encode());
                                } else {
                                    promise.complete(json);
                                }
                            }
                        } catch (Exception ex) {
                            promise.fail(ex);
                        }
                    })
                    .onFailure(promise::fail);

        } catch (Exception ex) {
            promise.fail(ex);
        }

        return promise.future();
    }

    /**
     * Send a JSON-RPC notification (no id, no expected response).
     */
    private void sendNotification(String method) {
        try {
            URI uri = URI.create(config.url());
            String host = uri.getHost();
            int port = uri.getPort() < 0 ? 443 : uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isBlank()) path = "/";

            JsonObject body = new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("method", method)
                    .put("params", new JsonObject());

            var req = webClient.post(port, host, path)
                    .ssl(true)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Authorization", "Bearer " + config.token())
                    .timeout(config.connectTimeoutMillis());

            String sid = sessionId.get();
            if (sid != null && !sid.isBlank()) {
                req = req.putHeader("Mcp-Session-Id", sid);
            }

            req.sendBuffer(Buffer.buffer(body.encode()))
                    .onFailure(err -> log.debug("[MCP-UPSTREAM] provider=github notification {} failed: {}",
                            method, err.getMessage()));
        } catch (Exception ex) {
            log.debug("[MCP-UPSTREAM] provider=github notification send exception: {}", ex.getMessage());
        }
    }

    /**
     * Parse an SSE body and return the {@code result} field of the event matching the given request id.
     */
    private static JsonObject parseSseBody(String body, String requestId) {
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            String data = trimmed.substring("data:".length()).trim();
            if (data.isBlank()) continue;
            try {
                JsonObject json = new JsonObject(data);
                String jsonId = json.getValue("id") != null ? String.valueOf(json.getValue("id")) : null;
                if (requestId.equals(jsonId)) {
                    if (json.containsKey("result")) {
                        return json.getJsonObject("result");
                    }
                    if (json.containsKey("error")) {
                        throw new RuntimeException("GitHub MCP error: " + json.getJsonObject("error").encode());
                    }
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignored) {
            }
        }
        // No matching event found; return empty object rather than failing hard
        return new JsonObject();
    }
}

