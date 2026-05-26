package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AcpAgentClient implements AutoCloseable {

    private final AcpProcessHandle processHandle;
    private final AcpJsonRpcConnection connection;
    private final Map<String, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Queue<JsonNode> queuedEvents = new ConcurrentLinkedQueue<>();
    private final Queue<CompletableFuture<JsonNode>> eventWaiters = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AcpAgentClient(AcpProcessHandle processHandle, AcpJsonRpcConnection connection) {
        this.processHandle = Objects.requireNonNull(processHandle, "processHandle must not be null");
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        pumpIncomingMessages();
    }

    public CompletableFuture<String> createSession(String cwd) {
        return createSession(cwd, Map.of());
    }

    public CompletableFuture<String> createSession(String cwd, Map<String, Object> mcpServers) {
        String normalizedCwd = requireText(cwd, "cwd");
        Map<String, Object> normalizedMcpServers = mcpServers == null ? Map.of() : Map.copyOf(mcpServers);
        return sendRequest("session/new", Map.of(
                        "cwd", normalizedCwd,
                        "mcpServers", normalizedMcpServers
                ))
                .thenApply(this::extractSessionId);
    }

    public CompletableFuture<Void> sendPrompt(String sessionId, String prompt) {
        String normalizedSessionId = requireText(sessionId, "sessionId");
        Objects.requireNonNull(prompt, "prompt must not be null");
        return sendRequest("session/prompt", Map.of("sessionId", normalizedSessionId, "prompt", prompt))
                .thenApply(ignored -> null);
    }

    public CompletableFuture<JsonNode> receiveEvent() {
        JsonNode queuedEvent = queuedEvents.poll();
        if (queuedEvent != null) {
            return CompletableFuture.completedFuture(queuedEvent);
        }
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ACP agent client is closed"));
        }

        CompletableFuture<JsonNode> waiter = new CompletableFuture<>();
        eventWaiters.add(waiter);
        JsonNode eventAfterEnqueue = queuedEvents.poll();
        if (eventAfterEnqueue != null && eventWaiters.remove(waiter)) {
            waiter.complete(eventAfterEnqueue);
        }
        return waiter;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        IllegalStateException failure = new IllegalStateException("ACP agent client is closed");
        failPendingRequests(failure);
        failEventWaiters(failure);
        try {
            connection.close();
        } finally {
            processHandle.destroy();
        }
    }

    private CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ACP agent client is closed"));
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);
        try {
            connection.sendRequest(method, params, requestId);
            return responseFuture;
        } catch (RuntimeException exception) {
            pendingRequests.remove(requestId);
            responseFuture.completeExceptionally(exception);
            return responseFuture;
        }
    }

    private void pumpIncomingMessages() {
        if (closed.get()) {
            return;
        }

        connection.readNext().whenComplete((message, throwable) -> {
            if (closed.get()) {
                return;
            }
            if (throwable != null) {
                RuntimeException failure = new IllegalStateException("ACP stream terminated", unwrap(throwable));
                failPendingRequests(failure);
                failEventWaiters(failure);
                return;
            }

            routeIncomingMessage(message);
            pumpIncomingMessages();
        });
    }

    private void routeIncomingMessage(JsonNode message) {
        Objects.requireNonNull(message, "message must not be null");
        JsonNode idNode = message.get("id");
        if (idNode != null && !idNode.isNull()) {
            CompletableFuture<JsonNode> pendingRequest = pendingRequests.remove(idNode.asText());
            if (pendingRequest != null) {
                completePendingRequest(pendingRequest, message);
                return;
            }
        }
        publishEvent(message);
    }

    private void completePendingRequest(CompletableFuture<JsonNode> pendingRequest, JsonNode message) {
        JsonNode errorNode = message.get("error");
        if (errorNode != null && !errorNode.isNull()) {
            pendingRequest.completeExceptionally(new IllegalStateException("ACP request failed: " + errorNode));
            return;
        }
        pendingRequest.complete(message);
    }

    private void publishEvent(JsonNode message) {
        CompletableFuture<JsonNode> waiter = eventWaiters.poll();
        if (waiter != null) {
            waiter.complete(message);
            return;
        }
        queuedEvents.add(message);
    }

    private void failPendingRequests(Throwable failure) {
        pendingRequests.forEach((id, future) -> future.completeExceptionally(failure));
        pendingRequests.clear();
    }

    private void failEventWaiters(Throwable failure) {
        CompletableFuture<JsonNode> waiter;
        while ((waiter = eventWaiters.poll()) != null) {
            waiter.completeExceptionally(failure);
        }
    }

    private String extractSessionId(JsonNode response) {
        JsonNode result = response.get("result");
        if (result == null || result.isNull()) {
            throw new IllegalStateException("ACP session/new response is missing result");
        }

        String sessionId = text(result, "sessionId");
        if (sessionId != null) {
            return sessionId;
        }

        sessionId = text(result, "id");
        if (sessionId != null) {
            return sessionId;
        }

        throw new IllegalStateException("ACP session/new response is missing sessionId");
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        return cause;
    }
}
