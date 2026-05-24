package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AcpEventMapper {

    public void applyEvent(AgentTask task, JsonNode event, int maxEventTextChars) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(event, "event must not be null");
        if (maxEventTextChars <= 0) {
            throw new IllegalArgumentException("maxEventTextChars must be greater than 0");
        }

        if (!"session/update".equals(text(event, "method"))) {
            return;
        }

        JsonNode params = event.get("params");
        if (params == null || params.isNull()) {
            throw new IllegalStateException("ACP session/update event is missing params");
        }

        String type = text(params, "type");
        if (type == null) {
            throw new IllegalStateException("ACP session/update event is missing type");
        }

        switch (type) {
            case "text" -> {
                markRunning(task);
                task.addEvent(truncate(textValue(params, "text", "content", "value"), maxEventTextChars));
            }
            case "completion" -> {
                markRunning(task);
                task.complete(truncate(textValue(params, "result", "text", "content"), maxEventTextChars));
            }
            case "error" -> {
                markRunning(task);
                task.fail(truncate(textValue(params, "error", "message", "text"), maxEventTextChars));
            }
            case "permission_request" -> {
                if (task.state() == AgentTaskState.CREATED) {
                    task.markRunning();
                }
                task.requestPermission(new PendingPermission(
                        permissionId(params),
                        truncate(textValue(params, "description", "message", "text"), maxEventTextChars),
                        Instant.now()));
            }
            default -> {
            }
        }
    }

    private static void markRunning(AgentTask task) {
        if (task.state() == AgentTaskState.CREATED || task.state() == AgentTaskState.WAITING_FOR_PERMISSION) {
            task.markRunning();
        }
    }

    private static String permissionId(JsonNode params) {
        String permissionId = text(params, "permissionId");
        if (permissionId != null) {
            return permissionId;
        }
        permissionId = text(params, "id");
        if (permissionId != null) {
            return permissionId;
        }
        return UUID.randomUUID().toString();
    }

    private static String textValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                return value.asText();
            }
            return value.toString();
        }
        throw new IllegalStateException("ACP session/update event is missing required payload");
    }

    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
