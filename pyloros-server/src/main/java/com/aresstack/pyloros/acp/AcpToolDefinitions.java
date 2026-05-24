package com.aresstack.pyloros.acp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcpToolDefinitions {

    public static final String RUN_TASK = "run_task";
    public static final String START_TASK = "start_task";
    public static final String GET_TASK_STATUS = "get_task_status";
    public static final String GET_TASK_RESULT = "get_task_result";
    public static final String CANCEL_TASK = "cancel_task";

    private AcpToolDefinitions() {
    }

    public static List<Map<String, Object>> definitions() {
        return List.of(
                toolDefinition(
                        RUN_TASK,
                        "Runs an ACP task immediately and waits for the ACP session to complete.",
                        promptExecutionSchema()),
                toolDefinition(
                        START_TASK,
                        "Starts an ACP task in the background and returns its task id immediately.",
                        promptExecutionSchema()),
                toolDefinition(
                        GET_TASK_STATUS,
                        "Returns the current state of an ACP task and optionally its recent events.",
                        getTaskStatusSchema()),
                toolDefinition(
                        GET_TASK_RESULT,
                        "Returns the result of a completed ACP task.",
                        taskIdSchema()),
                toolDefinition(
                        CANCEL_TASK,
                        "Requests cancellation for a running ACP task.",
                        taskIdSchema())
        );
    }

    private static Map<String, Object> promptExecutionSchema() {
        return schema(
                Map.of(
                        "prompt", Map.of("type", "string"),
                        "cwd", Map.of("type", "string"),
                        "timeoutSeconds", Map.of("type", "integer")
                ),
                List.of("prompt")
        );
    }

    private static Map<String, Object> getTaskStatusSchema() {
        return schema(
                Map.of(
                        "taskId", Map.of("type", "string"),
                        "includeEvents", Map.of("type", "boolean"),
                        "maxEvents", Map.of("type", "integer")
                ),
                List.of("taskId")
        );
    }

    private static Map<String, Object> taskIdSchema() {
        return schema(
                Map.of("taskId", Map.of("type", "string")),
                List.of("taskId")
        );
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> toolDefinition(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> toolDefinition = new LinkedHashMap<>();
        toolDefinition.put("name", name);
        toolDefinition.put("description", description);
        toolDefinition.put("inputSchema", inputSchema);
        return Map.copyOf(toolDefinition);
    }
}
