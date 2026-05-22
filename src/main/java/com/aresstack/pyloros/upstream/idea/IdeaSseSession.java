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
            String chunk = buff.toString(StandardCharsets.UTF_8);
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

