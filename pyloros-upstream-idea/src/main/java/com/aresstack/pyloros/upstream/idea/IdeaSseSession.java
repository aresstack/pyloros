package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE session that connects to the IDEA MCP SSE endpoint and extracts "endpoint" events.
 *
 * <p>Connection lifecycle rules, all of which the earlier implementation violated:
 * <ul>
 *   <li>At most one connection attempt is in flight at a time.</li>
 *   <li>At most one reconnect is scheduled at a time. A single drop fires both
 *       {@code exceptionHandler} and {@code endHandler}, so an unguarded reconnect doubled the
 *       number of live connections on every drop cycle.</li>
 *   <li>Each attempt carries a generation number. Callbacks belonging to a superseded attempt are
 *       ignored, so a late event from an abandoned connection can no longer clear the endpoint that
 *       a newer, healthy connection just discovered.</li>
 * </ul>
 *
 * <p>This never fails the application when IDEA is not reachable.
 */
public final class IdeaSseSession {

    private static final Logger log = LoggerFactory.getLogger(IdeaSseSession.class);

    /** Sentinel for "no attempt owns the connection state right now". */
    private static final long NO_ATTEMPT = -1L;

    private final Vertx vertx;
    private final IdeaMcpConfig config;

    private volatile String currentEndpoint;
    private volatile boolean closed = false;

    private HttpClient client;
    // pending responses keyed by id published to IDEA and answered back via SSE 'message' events
    private final Map<String, Promise<JsonObject>> pendingResponses = new ConcurrentHashMap<>();
    // optional handler invoked for incoming notification messages (e.g. notifications/tools/list_changed)
    private volatile Handler<JsonObject> notificationHandler;
    // optional handler invoked when SSE connection is lost and endpoint becomes stale
    private volatile Handler<String> disconnectHandler;

    // Monotonic counter identifying connection attempts.
    private final AtomicLong attemptCounter = new AtomicLong();
    // The attempt whose callbacks are currently allowed to mutate connection state.
    private volatile long activeAttempt = NO_ATTEMPT;
    // True while a connection attempt has been started but has neither established nor failed.
    private final AtomicBoolean connectInFlight = new AtomicBoolean(false);
    // True while a reconnect timer is armed.
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private volatile Long reconnectTimerId;

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
            // A bounded connect timeout matters for the in-flight guard below: a handshake that
            // never completes would otherwise keep every later reconnect from starting.
            client = vertx.createHttpClient(new io.vertx.core.http.HttpClientOptions()
                    .setConnectTimeout(Math.max(1000, config.connectTimeoutMillis())));
        }

        connect();
    }

    private void connect() {
        if (closed) return;
        if (!connectInFlight.compareAndSet(false, true)) {
            log.debug("IDEA SSE connect skipped, an attempt is already in flight");
            return;
        }

        long attempt = attemptCounter.incrementAndGet();
        activeAttempt = attempt;

        String host = config.host();
        int port = config.port();
        String path = config.ssePath();

        try {
            // Use request(...) which may return a future for the request in this Vert.x version
            client.request(io.vertx.core.http.HttpMethod.GET, port, host, path)
                    .onSuccess(req -> {
                        req.putHeader("Accept", "text/event-stream");
                        config.headers().forEach(req::putHeader);
                        req.exceptionHandler(err -> handleConnectionLost(attempt, "connection error", err));
                        req.send()
                                .onSuccess(response -> handleResponse(attempt, response))
                                .onFailure(err -> handleConnectionLost(attempt, "request send failure", err));
                    })
                    .onFailure(err -> handleConnectionLost(attempt, "request creation failure", err));
        } catch (Exception ex) {
            handleConnectionLost(attempt, "connection bootstrap exception", ex);
        }
    }

    private void handleResponse(long attempt, HttpClientResponse response) {
        if (isSuperseded(attempt)) {
            log.debug("Ignoring IDEA SSE response from superseded attempt {}", attempt);
            return;
        }

        if (response.statusCode() >= 400) {
            handleConnectionLost(attempt, "sse status " + response.statusCode(), null);
            return;
        }

        // The connection is established; a future drop is allowed to trigger a fresh attempt.
        connectInFlight.set(false);

        StringBuilder sb = new StringBuilder();

        response.handler(buff -> {
            if (isSuperseded(attempt)) {
                return;
            }
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

        response.exceptionHandler(err -> handleConnectionLost(attempt, "response exception", err));
        response.endHandler(v -> handleConnectionLost(attempt, "response ended", null));
    }

    /**
     * Single entry point for every way a connection attempt can die.
     *
     * <p>Callbacks from a superseded attempt are dropped. The first callback of the current attempt
     * retires that attempt, so the second callback of the same drop — a single drop delivers both an
     * exception and an end event — is itself superseded and cannot schedule a second reconnect.
     *
     * <p>Package-private so tests can replay late and duplicate callbacks deterministically.
     */
    void handleConnectionLost(long attempt, String reason, Throwable cause) {
        if (isSuperseded(attempt)) {
            log.debug("Ignoring late IDEA SSE event '{}' from superseded attempt {}", reason, attempt);
            return;
        }

        log.debug("IDEA SSE connection lost (attempt {}): {}", attempt, reason);
        activeAttempt = NO_ATTEMPT;
        connectInFlight.set(false);
        markStaleAndFailPending(reason, cause);
        scheduleReconnect();
    }

    private boolean isSuperseded(long attempt) {
        return closed || activeAttempt != attempt;
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
     * Register a handler that is invoked when the upstream connection is lost.
     */
    public void setDisconnectHandler(Handler<String> handler) {
        this.disconnectHandler = handler;
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
        if (!reconnectPending.compareAndSet(false, true)) {
            log.debug("IDEA SSE reconnect already scheduled");
            return;
        }

        int delay = Math.max(1000, config.connectTimeoutMillis());
        reconnectTimerId = vertx.setTimer(delay, id -> {
            reconnectTimerId = null;
            reconnectPending.set(false);
            connect();
        });
    }

    public boolean isReady() {
        return currentEndpoint != null && !currentEndpoint.isBlank();
    }

    public String getEndpoint() {
        return currentEndpoint;
    }

    /** Package-private test hook: the attempt that currently owns the connection state. */
    long activeAttempt() {
        return activeAttempt;
    }

    /** Package-private test hook: number of connection attempts started so far. */
    long attemptCount() {
        return attemptCounter.get();
    }

    public Future<Void> stop() {
        closed = true;
        activeAttempt = NO_ATTEMPT;
        cancelReconnect();
        markStaleAndFailPending("session stopped", null);
        try {
            if (client != null) {
                client.close();
                client = null;
            }
        } catch (Exception ignored) {
        }
        return Future.succeededFuture((Void) null);
    }

    private void cancelReconnect() {
        Long timerId = reconnectTimerId;
        reconnectTimerId = null;
        reconnectPending.set(false);
        if (timerId != null) {
            try {
                vertx.cancelTimer(timerId);
            } catch (Exception ex) {
                log.debug("Failed to cancel IDEA SSE reconnect timer: {}", ex.getMessage());
            }
        }
    }

    private void markStaleAndFailPending(String reason, Throwable cause) {
        currentEndpoint = null;

        if (disconnectHandler != null) {
            try {
                disconnectHandler.handle(reason);
            } catch (Exception ex) {
                log.debug("Disconnect handler failed: {}", ex.getMessage());
            }
        }

        if (pendingResponses.isEmpty()) {
            return;
        }

        String message = cause == null
                ? "IDEA MCP upstream disconnected (" + reason + ")"
                : "IDEA MCP upstream disconnected (" + reason + "): " + cause.getMessage();
        IllegalStateException failure = new IllegalStateException(message, cause);

        List<String> ids = new ArrayList<>(pendingResponses.keySet());
        for (String id : ids) {
            Promise<JsonObject> removed = pendingResponses.remove(id);
            if (removed != null) {
                removed.tryFail(failure);
            }
        }
    }
}
