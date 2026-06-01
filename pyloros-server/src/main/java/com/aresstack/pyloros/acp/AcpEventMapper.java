package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
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

        JsonNode update = params.get("update");
        if (update != null && !update.isNull()) {
            applyNestedUpdate(task, update, maxEventTextChars);
            return;
        }

        // Handle stopReason-only events (e.g., permission_required, end_turn)
        String stopReason = text(params, "stopReason");
        if (stopReason != null) {
            handleStopReason(task, stopReason, params, maxEventTextChars);
            return;
        }

        String type = text(params, "type");
        if (type == null) {
            task.addEvent(truncate(params.toString(), maxEventTextChars));
            return;
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

    public void applyPromptResponse(AgentTask task, JsonNode response, int maxEventTextChars) {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(response, "response must not be null");
        if (maxEventTextChars <= 0) {
            throw new IllegalArgumentException("maxEventTextChars must be greater than 0");
        }

        JsonNode result = response.get("result");
        if (result == null || result.isNull()) {
            return;
        }

        String stopReason = text(result, "stopReason");
        if (stopReason == null) {
            return;
        }

        handleStopReason(task, stopReason, result, maxEventTextChars);
    }

    private static void applyNestedUpdate(AgentTask task, JsonNode update, int maxEventTextChars) {
        String sessionUpdate = text(update, "sessionUpdate");
        if (sessionUpdate == null) {
            task.addEvent(truncate(update.toString(), maxEventTextChars));
            return;
        }

        switch (sessionUpdate) {
            case "agent_message_chunk", "agent_thought_chunk", "user_message_chunk" -> {
                markRunning(task);
                task.addEvent(truncate(textValue(update.path("content"), "text", "content", "value"), maxEventTextChars));
            }
            case "tool_call" -> {
                markRunning(task);
                task.addEvent(truncate(textValue(update, "title", "name", "kind"), maxEventTextChars));
            }
            case "tool_call_update" -> {
                markRunning(task);
                task.addEvent(truncate(toolCallUpdateText(update), maxEventTextChars));
            }
            case "plan" -> {
                markRunning(task);
                task.addEvent(truncate(update.toString(), maxEventTextChars));
            }
            default -> task.addEvent(truncate(update.toString(), maxEventTextChars));
        }
    }

    private static void handleStopReason(AgentTask task, String stopReason, JsonNode params, int maxEventTextChars) {
        switch (stopReason) {
            case "permission_required" -> {
                if (task.state() == AgentTaskState.CREATED) {
                    task.markRunning();
                }
                String description = text(params, "description");
                if (description == null) {
                    description = text(params, "message");
                }
                if (description == null) {
                    description = "Permission required";
                }
                task.requestPermission(new PendingPermission(
                        permissionId(params),
                        truncate(description, maxEventTextChars),
                        Instant.now()));
            }
            case "end_turn" -> {
                markRunning(task);
                String result = text(params, "result");
                if (result == null) {
                    result = text(params, "text");
                }
                if (result == null) {
                    result = lastEvent(task);
                }
                if (result == null) {
                    result = "Task completed";
                }
                task.complete(truncate(result, maxEventTextChars));
            }
            default -> {
                // Other stopReasons are informational only
            }
        }
    }

    private static String toolCallUpdateText(JsonNode update) {
        String status = text(update, "status");
        String toolCallId = text(update, "toolCallId");
        StringBuilder event = new StringBuilder("Tool call");
        if (toolCallId != null) {
            event.append(' ').append(toolCallId);
        }
        if (status != null) {
            event.append(' ').append(status);
        }
        String output = text(update, "output");
        if (output != null) {
            event.append(": ").append(output);
            return event.toString();
        }
        JsonNode content = update.get("content");
        if (content != null && content.isArray() && !content.isEmpty()) {
            String text = text(content.get(0).path("content"), "text");
            if (text != null) {
                event.append(": ").append(text);
            }
        }
        return event.toString();
    }

    private static String lastEvent(AgentTask task) {
        List<String> events = task.events();
        return events.isEmpty() ? null : events.getLast();
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
