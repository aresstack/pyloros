package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve ACP permissions without silently authorizing sensitive agent actions.
 */
final class AcpTaskPermissionHandler implements AcpPermissionHandler {

    private final AgentTask task;
    private final AgentTaskRepository repository;
    private final Set<String> readOnlyTools;
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();

    AcpTaskPermissionHandler(AgentTask task, AgentTaskRepository repository, Set<String> readOnlyTools) {
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.readOnlyTools = Set.copyOf(Objects.requireNonNull(readOnlyTools, "readOnlyTools must not be null"));
    }

    @Override
    public CompletionStage<Map<String, Object>> requestPermission(JsonNode params) {
        String title = params.path("toolCall").path("title").asText("");
        String allowOnce = optionId(params, "allow_once");
        if (allowOnce != null && isTrustedReadOnlyTool(title)) {
            task.addEvent("Automatically approved read-only tool: " + title);
            repository.save(task);
            return CompletableFuture.completedFuture(selected(allowOnce));
        }

        String permissionId = UUID.randomUUID().toString();
        String rejectOnce = optionId(params, "reject_once");
        CompletableFuture<Map<String, Object>> decision = new CompletableFuture<>();
        PendingRequest request = new PendingRequest(allowOnce, rejectOnce, decision);
        pending.put(permissionId, request);
        task.requestPermission(new PendingPermission(
                permissionId, title.isBlank() ? "Agent requests permission" : title, Instant.now()));
        repository.save(task);
        return decision;
    }

    boolean respond(String permissionId, boolean approve) {
        PendingRequest request = pending.get(permissionId);
        if (request == null) {
            return false;
        }
        if (approve && request.allowOnce() == null) {
            return false;
        }
        if (!pending.remove(permissionId, request)) {
            return false;
        }

        Map<String, Object> answer = approve
                ? selected(request.allowOnce())
                : request.rejectOnce() == null ? cancelled() : selected(request.rejectOnce());
        task.markRunning();
        task.addEvent(approve ? "Permission approved once" : "Permission rejected");
        repository.save(task);
        request.response().complete(answer);
        return true;
    }

    void cancelPending() {
        pending.forEach((permissionId, request) -> {
            if (pending.remove(permissionId, request)) {
                request.response().complete(cancelled());
            }
        });
    }

    private boolean isTrustedReadOnlyTool(String title) {
        return title.startsWith("pyloros_")
                && readOnlyTools.contains(title.substring("pyloros_".length()));
    }

    private static String optionId(JsonNode params, String kind) {
        for (JsonNode option : params.path("options")) {
            if (kind.equals(option.path("kind").asText())) {
                String id = option.path("optionId").asText("");
                return id.isBlank() ? null : id;
            }
        }
        return null;
    }

    private static Map<String, Object> selected(String optionId) {
        return Map.of("outcome", Map.of("outcome", "selected", "optionId", optionId));
    }

    private static Map<String, Object> cancelled() {
        return Map.of("outcome", Map.of("outcome", "cancelled"));
    }

    private record PendingRequest(String allowOnce, String rejectOnce,
                                  CompletableFuture<Map<String, Object>> response) {
    }
}
