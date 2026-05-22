package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Simple SSE session that connects to IDEA MCP SSE endpoint and extracts "endpoint" events.
 * This is a lightweight skeleton for 001-B. It will not fail the application when IDEA is not reachable.
 */
public final class IdeaSseSession {

    private static final Logger log = LoggerFactory.getLogger(IdeaSseSession.class);

    private final Vertx vertx;
    private final IdeaMcpConfig config;

    private volatile String currentEndpoint;
    private volatile boolean closed = false;

    private HttpClient client;
    // pending responses keyed by id published to IDEA and answered back via SSE 'message' events
    private final Map<String, Promise<JsonObject>> pendingResponses = new ConcurrentHashMap<>();
    // optional handler invoked for incoming notification messages (e.g. notifications/tools/list_changed)
    private volatile Handler<JsonObject> notificationHandler;

    public IdeaSseSession(Vertx vertx, IdeaMcpConfig config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void start() {
        if (closed) return;
        if (!config.enabled()) {
            log.info("IdeaSseSession disabled by configuration");
            return;
        }

        if (client == null) {
            client = vertx.createHttpClient();
        }

        connect();
    }

    private void connect() {
        String host = config.host();
        int port = config.port();
        String path = config.ssePath();

        try {
            // Use request(...) which may return a future for the request in this Vert.x version
            client.request(io.vertx.core.http.HttpMethod.GET, port, host, path)
                    .onSuccess(req -> {
                        req.putHeader("Accept", "text/event-stream");
                        // If a fixed access token is provided via environment for local testing, send it.
                        String token = System.getenv("OAUTH_ACCESS_TOKEN");
                        if (token != null && !token.isBlank()) {
                            req.putHeader("Authorization", "Bearer " + token);
                        }
                        req.exceptionHandler(err -> {
                            log.debug("IDEA SSE connection error: {}", err.getMessage());
                            scheduleReconnect();
                        });
                        req.send()
                                .onSuccess(this::handleResponse)
                                .onFailure(err -> {
                                    log.debug("Failed to send IDEA SSE request: {}", err.getMessage());
                                    scheduleReconnect();
                                });
                    })
                    .onFailure(err -> {
                        log.debug("Failed to create IDEA SSE request: {}", err.getMessage());
                        scheduleReconnect();
                    });
        } catch (Exception ex) {
            log.debug("Exception while starting IDEA SSE connection: {}", ex.getMessage());
            scheduleReconnect();
        }
    }

    private void handleResponse(HttpClientResponse response) {
        if (response.statusCode() >= 400) {
            log.debug("IDEA SSE responded with status {}", response.statusCode());
            scheduleReconnect();
            return;
        }

        StringBuilder sb = new StringBuilder();

        response.handler(buff -> {
            String chunk = buff.toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n');
            sb.append(chunk);
            // process complete events separated by double-newline
            String all = sb.toString();
            int idx;
            while ((idx = all.indexOf("\n\n")) != -1) {
                String eventBlock = all.substring(0, idx);
                processEventBlock(eventBlock);
                all = all.substring(idx + 2);
            }
            sb.setLength(0);
            sb.append(all);
        });

        response.exceptionHandler(err -> {
            log.debug("IDEA SSE response error: {}", err.getMessage());
            scheduleReconnect();
        });

        response.endHandler(v -> {
            log.debug("IDEA SSE connection ended");
            scheduleReconnect();
        });
    }

    private void processEventBlock(String block) {
        String[] lines = block.split("\n");
        String event = null;
        StringBuilder data = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("event:")) {
                event = trimmed.substring("event:".length()).trim();
            } else if (trimmed.startsWith("data:")) {
                data.append(trimmed.substring("data:".length()).trim());
            }
        }

        if ("endpoint" .equals(event) && data.length() > 0) {
            String endpoint = data.toString();
            this.currentEndpoint = endpoint;
            log.info("IDEA SSE endpoint discovered: {}", endpoint);
            return;
        }

        if ("message".equals(event) && data.length() > 0) {
            try {
                JsonObject json = new JsonObject(data.toString());
                // If this is a notification for tools list changes, inform registered handler
                try {
                    if (json.containsKey("method") && "notifications/tools/list_changed".equals(json.getString("method"))) {
                        if (notificationHandler != null) {
                            // pass the full message (including params) to the handler
                            notificationHandler.handle(json);
                        }
                        return;
                    }
                } catch (Exception ignore) {
                    // fall through to other handling
                }
                // If a pending promise exists for this id, complete it with the result (or the whole message)
                if (json.containsKey("id")) {
                    String id = String.valueOf(json.getValue("id"));
                    Promise<JsonObject> p = pendingResponses.remove(id);
                    if (p != null) {
                        if (json.containsKey("result")) {
                            p.tryComplete(json.getJsonObject("result"));
                        } else {
                            p.tryComplete(json);
                        }
                        return;
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to parse IDEA SSE message event: {}", ex.getMessage());
            }
        }
    }

    /**
     * Register a handler for notification messages coming from IDEA over SSE.
     * The handler will receive the full JSON message (contains method and params).
     */
    public void setNotificationHandler(Handler<JsonObject> handler) {
        this.notificationHandler = handler;
    }

    /**
     * Register a pending response promise for a request id and schedule timeout.
     */
    public void registerPendingResponse(String id, Promise<JsonObject> promise, long timeoutMillis) {
        pendingResponses.put(id, promise);
        if (timeoutMillis > 0) {
            vertx.setTimer(timeoutMillis, t -> {
                Promise<JsonObject> p = pendingResponses.remove(id);
                if (p != null && !p.future().isComplete()) {
                    p.tryFail(new IllegalStateException("Timeout waiting for IDEA SSE response for id " + id));
                }
            });
        }
    }

    private void scheduleReconnect() {
        if (closed) return;
        int delay = Math.max(1000, config.connectTimeoutMillis());
        vertx.setTimer(delay, id -> connect());
    }

    public boolean isReady() {
        return currentEndpoint != null && !currentEndpoint.isBlank();
    }

    public String getEndpoint() {
        return currentEndpoint;
    }

    public Future<Void> stop() {
        closed = true;
        try {
            if (client != null) {
                client.close();
                client = null;
            }
        } catch (Exception ignored) {
        }
        return Future.succeededFuture((Void) null);
    }
}

