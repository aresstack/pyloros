package com.aresstack.pyloros.acp;

import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AcpVirtualToolProvider implements ToolProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AcpProviderConfiguration config;
    private final AgentTaskRepository agentTaskRepository;

    public AcpVirtualToolProvider(AcpProviderConfiguration config, AgentTaskRepository agentTaskRepository) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.agentTaskRepository = Objects.requireNonNull(agentTaskRepository, "agentTaskRepository must not be null");
    }

    @Override
    public String providerId() {
        return config.id();
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.ACP;
    }

    @Override
    public List<ToolView> exposedViews() {
        return config.exposeInViews().stream()
                .map(ToolView::named)
                .toList();
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return Future.succeededFuture(AcpToolDefinitions.definitions());
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        JsonNode normalizedArguments = normalizeArguments(arguments);
        if (normalizedArguments == null) {
            return Future.succeededFuture(errorResult("Invalid arguments: expected an object"));
        }

        return Future.succeededFuture(switch (upstreamToolName) {
            case AcpToolDefinitions.RUN_TASK -> runTask(normalizedArguments);
            case AcpToolDefinitions.START_TASK -> startTask(normalizedArguments);
            case AcpToolDefinitions.GET_TASK_STATUS -> getTaskStatus(normalizedArguments);
            case AcpToolDefinitions.GET_TASK_RESULT -> getTaskResult(normalizedArguments);
            case AcpToolDefinitions.CANCEL_TASK -> cancelTask(normalizedArguments);
            default -> errorResult("Unknown tool: " + upstreamToolName);
        });
    }

    private Map<String, Object> runTask(JsonNode arguments) {
        String prompt = requiredText(arguments, "prompt");
        if (prompt == null) {
            return errorResult("Invalid arguments: prompt is required");
        }

        String cwd = optionalText(arguments, "cwd");
        if (hasField(arguments, "cwd") && cwd == null) {
            return errorResult("Invalid arguments: cwd must be a string");
        }

        Integer timeoutSeconds = optionalPositiveInteger(arguments, "timeoutSeconds");
        if (hasField(arguments, "timeoutSeconds") && timeoutSeconds == null) {
            return errorResult("Invalid arguments: timeoutSeconds must be a positive integer");
        }

        AgentTask task = new AgentTask(
                AgentTaskId.generate(),
                providerId(),
                cwd,
                prompt,
                config.execution().maxEventsPerTask());
        agentTaskRepository.save(task);
        task.markRunning();
        task.addEvent("Task started");
        task.addEvent("Placeholder ACP execution completed");
        task.complete(buildPlaceholderResult(prompt, cwd, timeoutSeconds == null ? config.execution().defaultTaskTimeoutSeconds() : timeoutSeconds));
        agentTaskRepository.save(task);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("state", task.state().name());
        payload.put("result", task.result());
        payload.put("task", taskDetails(task, false, null));
        return successResult(payload);
    }

    private Map<String, Object> startTask(JsonNode arguments) {
        String prompt = requiredText(arguments, "prompt");
        if (prompt == null) {
            return errorResult("Invalid arguments: prompt is required");
        }

        String cwd = optionalText(arguments, "cwd");
        if (hasField(arguments, "cwd") && cwd == null) {
            return errorResult("Invalid arguments: cwd must be a string");
        }

        Integer timeoutSeconds = optionalPositiveInteger(arguments, "timeoutSeconds");
        if (hasField(arguments, "timeoutSeconds") && timeoutSeconds == null) {
            return errorResult("Invalid arguments: timeoutSeconds must be a positive integer");
        }

        AgentTask task = new AgentTask(
                AgentTaskId.generate(),
                providerId(),
                cwd,
                prompt,
                config.execution().maxEventsPerTask());
        agentTaskRepository.save(task);
        task.markRunning();
        task.addEvent("Task started");
        task.addEvent("Timeout seconds: " + (timeoutSeconds == null ? config.execution().defaultTaskTimeoutSeconds() : timeoutSeconds));
        agentTaskRepository.save(task);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("state", task.state().name());
        payload.put("task", taskDetails(task, false, null));
        return successResult(payload);
    }

    private Map<String, Object> getTaskStatus(JsonNode arguments) {
        TaskLookup taskLookup = lookupTask(arguments);
        if (taskLookup.errorMessage() != null) {
            return errorResult(taskLookup.errorMessage());
        }
        AgentTask task = taskLookup.task();

        Boolean includeEvents = optionalBoolean(arguments, "includeEvents");
        if (hasField(arguments, "includeEvents") && includeEvents == null) {
            return errorResult("Invalid arguments: includeEvents must be a boolean");
        }

        Integer maxEvents = optionalPositiveInteger(arguments, "maxEvents");
        if (hasField(arguments, "maxEvents") && maxEvents == null) {
            return errorResult("Invalid arguments: maxEvents must be a positive integer");
        }

        return successResult(taskDetails(task, Boolean.TRUE.equals(includeEvents), maxEvents));
    }

    private Map<String, Object> getTaskResult(JsonNode arguments) {
        TaskLookup taskLookup = lookupTask(arguments);
        if (taskLookup.errorMessage() != null) {
            return errorResult(taskLookup.errorMessage());
        }
        AgentTask task = taskLookup.task();

        if (task.state() == AgentTaskState.COMPLETED) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.taskId().value());
            payload.put("state", task.state().name());
            payload.put("result", task.result());
            payload.put("completedAt", instantText(task.completedAt()));
            return successResult(payload);
        }

        if (task.state() == AgentTaskState.RUNNING || task.state() == AgentTaskState.CREATED || task.state() == AgentTaskState.WAITING_FOR_PERMISSION) {
            return errorResult("Task result is not available while task is in state " + task.state().name());
        }

        return errorResult("Task did not complete successfully: " + task.state().name());
    }

    private Map<String, Object> cancelTask(JsonNode arguments) {
        TaskLookup taskLookup = lookupTask(arguments);
        if (taskLookup.errorMessage() != null) {
            return errorResult(taskLookup.errorMessage());
        }
        AgentTask task = taskLookup.task();

        try {
            task.cancel();
            task.addEvent("Cancellation requested");
            agentTaskRepository.save(task);
        } catch (IllegalStateException exception) {
            return errorResult(exception.getMessage());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("state", task.state().name());
        payload.put("cancellationRequested", task.cancellationRequested());
        return successResult(payload);
    }

    private TaskLookup lookupTask(JsonNode arguments) {
        String taskId = requiredText(arguments, "taskId");
        if (taskId == null) {
            return new TaskLookup(null, "Invalid arguments: taskId is required");
        }

        AgentTaskId parsedTaskId;
        try {
            parsedTaskId = AgentTaskId.of(taskId);
        } catch (IllegalArgumentException exception) {
            return new TaskLookup(null, "Invalid arguments: taskId must be a valid UUID");
        }

        AgentTask task = agentTaskRepository.findById(parsedTaskId).orElse(null);
        if (task == null) {
            return new TaskLookup(null, "Unknown taskId: " + taskId);
        }
        return new TaskLookup(task, null);
    }

    private Map<String, Object> taskDetails(AgentTask task, boolean includeEvents, Integer maxEvents) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("providerId", task.providerId());
        payload.put("state", task.state().name());
        payload.put("cwd", task.cwd());
        payload.put("promptPreview", task.promptPreview());
        payload.put("promptHash", task.promptHash());
        payload.put("startedAt", instantText(task.startedAt()));
        payload.put("updatedAt", instantText(task.updatedAt()));
        payload.put("completedAt", instantText(task.completedAt()));
        payload.put("cancellationRequested", task.cancellationRequested());
        payload.put("pendingPermissions", task.pendingPermissions());
        if (includeEvents) {
            payload.put("events", limitedEvents(task.events(), maxEvents));
        }
        return payload;
    }

    private List<String> limitedEvents(List<String> events, Integer maxEvents) {
        if (maxEvents == null || events.size() <= maxEvents) {
            return events;
        }
        return events.subList(events.size() - maxEvents, events.size());
    }

    private String buildPlaceholderResult(String prompt, String cwd, int timeoutSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", trimToMaxLength("Placeholder ACP execution result for prompt: " + prompt, config.execution().maxResultTextChars()));
        payload.put("cwd", cwd);
        payload.put("timeoutSeconds", timeoutSeconds);
        return toJson(payload);
    }

    private Map<String, Object> successResult(Object payload) {
        return toolResult(payload, false);
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        return toolResult(payload, true);
    }

    private Map<String, Object> toolResult(Object payload, boolean isError) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", toJson(payload))),
                "isError", isError
        );
    }

    private String toJson(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize ACP tool result", exception);
        }
    }

    private String trimToMaxLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private JsonNode normalizeArguments(JsonNode arguments) {
        if (arguments == null || arguments.isNull()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return arguments.isObject() ? arguments : null;
    }

    private boolean hasField(JsonNode arguments, String fieldName) {
        return arguments.has(fieldName) && !arguments.get(fieldName).isNull();
    }

    private String requiredText(JsonNode arguments, String fieldName) {
        String text = optionalText(arguments, fieldName);
        return text == null || text.isBlank() ? null : text;
    }

    private String optionalText(JsonNode arguments, String fieldName) {
        if (!hasField(arguments, fieldName)) {
            return null;
        }
        JsonNode node = arguments.get(fieldName);
        if (!node.isTextual()) {
            return null;
        }
        return node.asText().trim();
    }

    private Integer optionalPositiveInteger(JsonNode arguments, String fieldName) {
        if (!hasField(arguments, fieldName)) {
            return null;
        }
        JsonNode node = arguments.get(fieldName);
        if (!node.isIntegralNumber()) {
            return null;
        }
        int value = node.asInt();
        return value > 0 ? value : null;
    }

    private Boolean optionalBoolean(JsonNode arguments, String fieldName) {
        if (!hasField(arguments, fieldName)) {
            return null;
        }
        JsonNode node = arguments.get(fieldName);
        return node.isBoolean() ? node.asBoolean() : null;
    }

    private String instantText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private record TaskLookup(AgentTask task, String errorMessage) {
    }
}
