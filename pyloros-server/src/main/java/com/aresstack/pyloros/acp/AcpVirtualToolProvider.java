package com.aresstack.pyloros.acp;

import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AcpVirtualToolProvider implements ToolProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long EVENT_POLL_MILLIS = 200L;
    private static final int STDERR_PREVIEW_CHARS = 512;
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "acp-task-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private final Vertx vertx;
    private final AcpProviderConfiguration config;
    private final AgentTaskRepository agentTaskRepository;
    private final AcpProcessLauncher processLauncher;
    private final AcpAuditLogger auditLogger;
    private final AcpEventMapper eventMapper;
    private final ConcurrentHashMap<AgentTaskId, AcpProcessHandle> activeProcesses = new ConcurrentHashMap<>();

    public AcpVirtualToolProvider(Vertx vertx, AcpProviderConfiguration config, AgentTaskRepository agentTaskRepository) {
        this(vertx, config, agentTaskRepository, new AcpProcessLauncher(), new AcpAuditLogger(), new AcpEventMapper());
    }

    AcpVirtualToolProvider(
            Vertx vertx,
            AcpProviderConfiguration config,
            AgentTaskRepository agentTaskRepository,
            AcpProcessLauncher processLauncher,
            AcpAuditLogger auditLogger,
            AcpEventMapper eventMapper) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.agentTaskRepository = Objects.requireNonNull(agentTaskRepository, "agentTaskRepository must not be null");
        this.processLauncher = Objects.requireNonNull(processLauncher, "processLauncher must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
        this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper must not be null");
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

        return switch (upstreamToolName) {
            case AcpToolDefinitions.RUN_TASK -> runTask(normalizedArguments);
            case AcpToolDefinitions.START_TASK -> startTask(normalizedArguments);
            case AcpToolDefinitions.GET_TASK_STATUS -> Future.succeededFuture(getTaskStatus(normalizedArguments));
            case AcpToolDefinitions.GET_TASK_RESULT -> Future.succeededFuture(getTaskResult(normalizedArguments));
            case AcpToolDefinitions.CANCEL_TASK -> Future.succeededFuture(cancelTask(normalizedArguments));
            default -> Future.succeededFuture(errorResult("Unknown tool: " + upstreamToolName));
        };
    }

    private Future<Map<String, Object>> runTask(JsonNode arguments) {
        ParsedExecutionRequest request = parseExecutionRequest(arguments);
        if (request.errorMessage() != null) {
            return Future.succeededFuture(errorResult(request.errorMessage()));
        }

        AgentTask task = createTask(request);
        return vertx.executeBlocking(() -> executeRunTask(task, request), false);
    }

    private Future<Map<String, Object>> startTask(JsonNode arguments) {
        ParsedExecutionRequest request = parseExecutionRequest(arguments);
        if (request.errorMessage() != null) {
            return Future.succeededFuture(errorResult(request.errorMessage()));
        }

        AgentTask task = createTask(request);
        Thread.ofPlatform()
                .daemon(true)
                .name("acp-task-" + task.taskId().value())
                .start(() -> executeBackgroundTask(task, request));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("state", task.state().name());
        payload.put("task", taskDetails(task, false, null));
        return Future.succeededFuture(successResult(payload));
    }

    private Map<String, Object> executeRunTask(AgentTask task, ParsedExecutionRequest request) {
        executeTask(task, request);
        return terminalResult(task, true);
    }

    private void executeBackgroundTask(AgentTask task, ParsedExecutionRequest request) {
        try {
            executeTask(task, request);
        } catch (RuntimeException ignored) {
            // Task state already captures the failure and is exposed via repository lookups.
        }
    }

    private void executeTask(AgentTask task, ParsedExecutionRequest request) {
        long startedAt = System.nanoTime();
        AcpProcessHandle processHandle = null;
        ScheduledFuture<?> timeoutFuture = null;

        try {
            processHandle = processLauncher.launch(processConfigurationFor(request.cwd()));
            activeProcesses.put(task.taskId(), processHandle);
            timeoutFuture = scheduleTimeout(task, processHandle, request.timeoutSeconds());

            try (AcpJsonRpcConnection connection = new AcpJsonRpcConnection(processHandle.stdout(), processHandle.stdin());
                 AcpAgentClient client = new AcpAgentClient(processHandle, connection)) {
                String sessionId = client.createSession(resolveSessionCwd(request.cwd())).join();
                task.setAcpSessionId(sessionId);
                agentTaskRepository.save(task);

                client.sendPrompt(sessionId, request.prompt()).join();
                agentTaskRepository.save(task);

                waitForTerminalState(task, client, processHandle);
            }
        } catch (RuntimeException exception) {
            handleExecutionFailure(task, processHandle, exception);
        } finally {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(true);
            }
            if (processHandle != null) {
                activeProcesses.remove(task.taskId(), processHandle);
                processHandle.destroy();
            }
            agentTaskRepository.save(task);
            logTerminalState(task, startedAt);
        }
    }

    private AgentTask createTask(ParsedExecutionRequest request) {
        AgentTask task = new AgentTask(
                AgentTaskId.generate(),
                providerId(),
                request.cwd(),
                request.prompt(),
                config.execution().maxEventsPerTask());
        agentTaskRepository.save(task);
        task.markRunning();
        task.addEvent("Task started");
        agentTaskRepository.save(task);
        auditLogger.logTaskStarted(task);
        return task;
    }

    private void waitForTerminalState(AgentTask task, AcpAgentClient client, AcpProcessHandle processHandle) {
        while (!isTerminal(task.state())) {
            try {
                JsonNode event = client.receiveEvent().get(EVENT_POLL_MILLIS, TimeUnit.MILLISECONDS);
                eventMapper.applyEvent(task, event, config.execution().maxEventTextChars());
                agentTaskRepository.save(task);
            } catch (TimeoutException ignored) {
                if (isTerminal(task.state())) {
                    return;
                }
                if (!processHandle.isAlive()) {
                    throw new IllegalStateException("ACP process exited before task completion" + stderrSuffix(processHandle));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("ACP task execution interrupted", exception);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }
    }

    private ScheduledFuture<?> scheduleTimeout(AgentTask task, AcpProcessHandle processHandle, int timeoutSeconds) {
        return TIMEOUT_EXECUTOR.schedule(() -> {
            if (isTerminal(task.state())) {
                return;
            }
            synchronized (task) {
                if (isTerminal(task.state())) {
                    return;
                }
                task.addEvent("Task timed out after " + timeoutSeconds + " seconds");
                task.timeout();
            }
            agentTaskRepository.save(task);
            processHandle.destroy();
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    private void handleExecutionFailure(AgentTask task, AcpProcessHandle processHandle, RuntimeException exception) {
        if (isTerminal(task.state())) {
            return;
        }
        Throwable cause = unwrap(exception);
        String errorMessage = failureMessage(cause, processHandle);
        task.addEvent(errorMessage);
        task.fail(trimToMaxLength(errorMessage, config.execution().maxResultTextChars()));
        agentTaskRepository.save(task);
    }

    private void logTerminalState(AgentTask task, long startedAt) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if (task.state() == AgentTaskState.COMPLETED) {
            auditLogger.logTaskCompleted(task, durationMs);
            return;
        }
        if (task.state() != AgentTaskState.RUNNING && task.state() != AgentTaskState.CREATED) {
            auditLogger.logTaskFailed(task, durationMs, task.state().name().toLowerCase());
        }
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

        if (isTerminal(task.state())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.taskId().value());
            payload.put("state", task.state().name());
            payload.put("cancellationRequested", task.cancellationRequested());
            payload.put("message", "Task already in terminal state: " + task.state().name());
            return successResult(payload);
        }

        try {
            task.addEvent("Cancellation requested");
            task.cancel();
            agentTaskRepository.save(task);
            AcpProcessHandle processHandle = activeProcesses.remove(task.taskId());
            if (processHandle != null) {
                processHandle.destroy();
            }
        } catch (IllegalStateException exception) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.taskId().value());
            payload.put("state", task.state().name());
            payload.put("cancellationRequested", task.cancellationRequested());
            payload.put("message", "Task transitioned to terminal state concurrently: " + task.state().name());
            return successResult(payload);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("state", task.state().name());
        payload.put("cancellationRequested", task.cancellationRequested());
        return successResult(payload);
    }

    private ParsedExecutionRequest parseExecutionRequest(JsonNode arguments) {
        String prompt = requiredText(arguments, "prompt");
        if (prompt == null) {
            return ParsedExecutionRequest.error("Invalid arguments: prompt is required");
        }

        String cwd = optionalText(arguments, "cwd");
        if (hasField(arguments, "cwd") && cwd == null) {
            return ParsedExecutionRequest.error("Invalid arguments: cwd must be a string");
        }

        Integer timeoutSeconds = optionalPositiveInteger(arguments, "timeoutSeconds");
        if (hasField(arguments, "timeoutSeconds") && timeoutSeconds == null) {
            return ParsedExecutionRequest.error("Invalid arguments: timeoutSeconds must be a positive integer");
        }

        return ParsedExecutionRequest.success(prompt, cwd, timeoutSeconds == null
                ? config.execution().defaultTaskTimeoutSeconds()
                : timeoutSeconds);
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
        payload.put("acpSessionId", task.acpSessionId());
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

    private AcpProcessConfiguration processConfigurationFor(String requestedCwd) {
        String workingDirectory = requestedCwd == null ? config.process().workingDirectory() : requestedCwd;
        return new AcpProcessConfiguration(
                config.process().command(),
                config.process().args(),
                workingDirectory,
                config.process().environment());
    }

    private String resolveSessionCwd(String requestedCwd) {
        if (requestedCwd != null) {
            return requestedCwd;
        }
        String configuredWorkingDirectory = config.process().workingDirectory();
        if (configuredWorkingDirectory != null && !configuredWorkingDirectory.isBlank()) {
            return configuredWorkingDirectory;
        }
        return System.getProperty("user.dir", ".");
    }

    private boolean isTerminal(AgentTaskState state) {
        return state == AgentTaskState.COMPLETED
                || state == AgentTaskState.FAILED
                || state == AgentTaskState.CANCELLED
                || state == AgentTaskState.TIMEOUT;
    }

    private String failureMessage(Throwable throwable, AcpProcessHandle processHandle) {
        if (throwable instanceof AcpProcessFailure processFailure) {
            return trimToMaxLength(processFailure.getMessage(), config.execution().maxResultTextChars());
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return trimToMaxLength(message + stderrSuffix(processHandle), config.execution().maxResultTextChars());
    }

    private String stderrSuffix(AcpProcessHandle processHandle) {
        if (processHandle == null) {
            return "";
        }
        String stderr = processHandle.collectStderr(STDERR_PREVIEW_CHARS).trim();
        return stderr.isEmpty() ? "" : "; stderr=" + stderr;
    }

    private Map<String, Object> terminalResult(AgentTask task, boolean includeResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.taskId().value());
        payload.put("state", task.state().name());
        if (includeResult && task.result() != null) {
            payload.put("result", task.result());
        }
        if (task.error() != null) {
            payload.put("error", task.error());
        }
        payload.put("task", taskDetails(task, true, null));
        return task.state() == AgentTaskState.COMPLETED ? successResult(payload) : errorResult(payload);
    }

    private Map<String, Object> successResult(Object payload) {
        return toolResult(payload, false);
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        return errorResult(payload);
    }

    private Map<String, Object> errorResult(Object payload) {
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

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException completionException && completionException.getCause() != null) {
            cause = completionException.getCause();
        }
        return cause;
    }

    private record TaskLookup(AgentTask task, String errorMessage) {
    }

    private record ParsedExecutionRequest(String prompt, String cwd, int timeoutSeconds, String errorMessage) {

        private static ParsedExecutionRequest success(String prompt, String cwd, int timeoutSeconds) {
            return new ParsedExecutionRequest(prompt, cwd, timeoutSeconds, null);
        }

        private static ParsedExecutionRequest error(String errorMessage) {
            return new ParsedExecutionRequest(null, null, 0, errorMessage);
        }
    }
}
