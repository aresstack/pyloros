package com.aresstack.pyloros.acp;

import com.aresstack.pyloros.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpVirtualToolProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void testProviderId() {
        AcpVirtualToolProvider provider = newProvider().provider();

        assertEquals("copilot", provider.providerId());
    }

    @Test
    void testProviderType() {
        AcpVirtualToolProvider provider = newProvider().provider();

        assertEquals(ProviderType.ACP, provider.providerType());
    }

    @Test
    void testListTools() {
        AcpVirtualToolProvider provider = newProvider().provider();

        List<Map<String, Object>> tools = await(provider.listTools());
        Set<String> names = toolNames(tools);

        assertEquals(5, tools.size());
        assertEquals(Set.of(
                "run_task",
                "start_task",
                "get_task_status",
                "get_task_result",
                "cancel_task"
        ), names);
    }

    @Test
    void testRunTaskCreatesTask() throws IOException {
        ProviderContext context = newProvider();

        JsonNode response = responsePayload(call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt")));
        AgentTask task = findTask(context.repository(), response.get("taskId").asText());

        assertEquals("COMPLETED", response.get("state").asText());
        assertNotNull(response.get("result").asText());
        assertEquals(AgentTaskState.COMPLETED, task.state());
        assertNotNull(task.result());
    }

    @Test
    void testStartTaskCreatesRunningTask() throws IOException {
        ProviderContext context = newProvider();

        JsonNode response = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));
        AgentTask task = findTask(context.repository(), response.get("taskId").asText());

        assertEquals("RUNNING", response.get("state").asText());
        assertEquals(AgentTaskState.RUNNING, task.state());
    }

    @Test
    void testGetTaskStatusForRunningTask() throws IOException {
        ProviderContext context = newProvider();
        JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));

        JsonNode response = responsePayload(call(context.provider(), "get_task_status", objectNode().put("taskId", started.get("taskId").asText())));

        assertEquals(started.get("taskId").asText(), response.get("taskId").asText());
        assertEquals("RUNNING", response.get("state").asText());
    }

    @Test
    void testGetTaskResultForCompletedTask() throws IOException {
        ProviderContext context = newProvider();
        JsonNode started = responsePayload(call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt")));

        JsonNode response = responsePayload(call(context.provider(), "get_task_result", objectNode().put("taskId", started.get("taskId").asText())));

        assertEquals("COMPLETED", response.get("state").asText());
        assertEquals(started.get("result").asText(), response.get("result").asText());
    }

    @Test
    void testGetTaskResultForRunningTask() throws IOException {
        ProviderContext context = newProvider();
        JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));

        Map<String, Object> result = call(context.provider(), "get_task_result", objectNode().put("taskId", started.get("taskId").asText()));
        JsonNode response = responsePayload(result);

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("Task result is not available while task is in state RUNNING", response.get("error").asText());
    }

    @Test
    void testCancelRunningTask() throws IOException {
        ProviderContext context = newProvider();
        JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));

        JsonNode response = responsePayload(call(context.provider(), "cancel_task", objectNode().put("taskId", started.get("taskId").asText())));
        AgentTask task = findTask(context.repository(), started.get("taskId").asText());

        assertEquals("CANCELLED", response.get("state").asText());
        assertTrue(response.get("cancellationRequested").asBoolean());
        assertEquals(AgentTaskState.CANCELLED, task.state());
    }

    @Test
    void testUnknownTaskId() throws IOException {
        ProviderContext context = newProvider();

        Map<String, Object> result = call(context.provider(), "get_task_status", objectNode().put("taskId", AgentTaskId.generate().value()));
        JsonNode response = responsePayload(result);

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertTrue(response.get("error").asText().startsWith("Unknown taskId: "));
    }

    @Test
    void testUnknownTool() throws IOException {
        ProviderContext context = newProvider();

        Map<String, Object> result = call(context.provider(), "does_not_exist", objectNode());
        JsonNode response = responsePayload(result);

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("Unknown tool: does_not_exist", response.get("error").asText());
    }

    @Test
    void testMissingPrompt() throws IOException {
        ProviderContext context = newProvider();

        Map<String, Object> result = call(context.provider(), "run_task", objectNode());
        JsonNode response = responsePayload(result);

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("Invalid arguments: prompt is required", response.get("error").asText());
    }

    @Test
    void testCancelAlreadyCompletedTaskReturnsError() throws IOException {
        ProviderContext context = newProvider();
        JsonNode started = responsePayload(call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt")));

        Map<String, Object> result = call(context.provider(), "cancel_task", objectNode().put("taskId", started.get("taskId").asText()));
        JsonNode response = responsePayload(result);

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("Cannot transition task from COMPLETED to CANCELLED", response.get("error").asText());
    }

    private static ProviderContext newProvider() {
        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "copilot", "copilot/", "agent", List.of("public"),
                new AcpProcessConfiguration("echo", List.of(), null, Map.of()),
                new AcpExecutionConfiguration()
        );
        AgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AcpVirtualToolProvider provider = new AcpVirtualToolProvider(config, repository);
        return new ProviderContext(provider, repository);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode objectNode() {
        return JSON.createObjectNode();
    }

    private static Map<String, Object> call(AcpVirtualToolProvider provider, String toolName, JsonNode arguments) {
        return await(provider.callTool(toolName, arguments));
    }

    private static JsonNode responsePayload(Map<String, Object> result) throws IOException {
        return JSON.readTree(firstText(result));
    }

    private static String firstText(Map<String, Object> result) {
        Object content = result.get("content");
        if (!(content instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        Object first = list.getFirst();
        if (!(first instanceof Map<?, ?> item)) {
            return "";
        }
        Object text = item.get("text");
        return text == null ? "" : text.toString();
    }

    private static Set<String> toolNames(List<Map<String, Object>> tools) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> tool : tools) {
            names.add((String) tool.get("name"));
        }
        return names;
    }

    private static AgentTask findTask(AgentTaskRepository repository, String taskId) {
        return repository.findById(AgentTaskId.of(taskId)).orElseThrow();
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private record ProviderContext(AcpVirtualToolProvider provider, AgentTaskRepository repository) {
    }
}
