package com.aresstack.pyloros.acp;

import com.aresstack.pyloros.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpVirtualToolProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void testProviderId() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            assertEquals("copilot", context.provider().providerId());
        }
    }

    @Test
    void testProviderType() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            assertEquals(ProviderType.ACP, context.provider().providerType());
        }
    }

    @Test
    void testListTools() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            List<Map<String, Object>> tools = await(context.provider().listTools());
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
    }

    @Test
    void testRunTaskCreatesTask() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            JsonNode response = responsePayload(call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt")));
            AgentTask task = findTask(context.repository(), response.get("taskId").asText());

            assertEquals("COMPLETED", response.get("state").asText());
            assertEquals("completed: Test prompt", response.get("result").asText());
            assertEquals(AgentTaskState.COMPLETED, task.state());
            assertEquals("completed: Test prompt", task.result());
            assertNotNull(task.acpSessionId());
        }
    }

    @Test
    void testRunTaskReturnsErrorPayloadOnFailure() throws Exception {
        try (ProviderContext context = newProvider("error")) {
            Map<String, Object> result = call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt"));
            JsonNode response = responsePayload(result);
            AgentTask task = findTask(context.repository(), response.get("taskId").asText());

            assertEquals(Boolean.TRUE, result.get("isError"));
            assertEquals("FAILED", response.get("state").asText());
            assertEquals("simulated failure", response.get("error").asText());
            assertEquals(AgentTaskState.FAILED, task.state());
        }
    }

    @Test
    void testRunTaskTimesOut() throws Exception {
        try (ProviderContext context = newProvider("timeout", 1)) {
            Map<String, Object> result = call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt"));
            JsonNode response = responsePayload(result);
            AgentTask task = findTask(context.repository(), response.get("taskId").asText());

            assertEquals(Boolean.TRUE, result.get("isError"));
            assertEquals("TIMEOUT", response.get("state").asText());
            assertEquals(AgentTaskState.TIMEOUT, task.state());
        }
    }

    @Test
    void testStartTaskCreatesRunningTask() throws Exception {
        try (ProviderContext context = newProvider("timeout", 10)) {
            JsonNode response = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));
            AgentTask task = findTask(context.repository(), response.get("taskId").asText());

            assertEquals("RUNNING", response.get("state").asText());
            assertEquals(AgentTaskState.RUNNING, task.state());
        }
    }

    @Test
    void testGetTaskStatusForRunningTask() throws Exception {
        try (ProviderContext context = newProvider("timeout", 10)) {
            JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));

            JsonNode response = responsePayload(call(context.provider(), "get_task_status", objectNode().put("taskId", started.get("taskId").asText())));

            assertEquals(started.get("taskId").asText(), response.get("taskId").asText());
            assertEquals("RUNNING", response.get("state").asText());
        }
    }

    @Test
    void testGetTaskResultForCompletedTask() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            JsonNode started = responsePayload(call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt")));

            JsonNode response = responsePayload(call(context.provider(), "get_task_result", objectNode().put("taskId", started.get("taskId").asText())));

            assertEquals("COMPLETED", response.get("state").asText());
            assertEquals(started.get("result").asText(), response.get("result").asText());
        }
    }

    @Test
    void testGetTaskResultForRunningTask() throws Exception {
        try (ProviderContext context = newProvider("timeout", 10)) {
            JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));

            Map<String, Object> result = call(context.provider(), "get_task_result", objectNode().put("taskId", started.get("taskId").asText()));
            JsonNode response = responsePayload(result);

            assertEquals(Boolean.TRUE, result.get("isError"));
            assertEquals("Task result is not available while task is in state RUNNING", response.get("error").asText());
        }
    }

    @Test
    void testCancelRunningTask() throws Exception {
        try (ProviderContext context = newProvider("timeout", 10)) {
            JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));

            JsonNode response = responsePayload(call(context.provider(), "cancel_task", objectNode().put("taskId", started.get("taskId").asText())));
            AgentTask task = findTask(context.repository(), started.get("taskId").asText());

            assertEquals("CANCELLED", response.get("state").asText());
            assertTrue(response.get("cancellationRequested").asBoolean());
            assertEquals(AgentTaskState.CANCELLED, task.state());
        }
    }

    @Test
    void testUnknownTaskId() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            Map<String, Object> result = call(context.provider(), "get_task_status", objectNode().put("taskId", AgentTaskId.generate().value()));
            JsonNode response = responsePayload(result);

            assertEquals(Boolean.TRUE, result.get("isError"));
            assertTrue(response.get("error").asText().startsWith("Unknown taskId: "));
        }
    }

    @Test
    void testUnknownTool() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            Map<String, Object> result = call(context.provider(), "does_not_exist", objectNode());
            JsonNode response = responsePayload(result);

            assertEquals(Boolean.TRUE, result.get("isError"));
            assertEquals("Unknown tool: does_not_exist", response.get("error").asText());
        }
    }

    @Test
    void testMissingPrompt() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            Map<String, Object> result = call(context.provider(), "run_task", objectNode());
            JsonNode response = responsePayload(result);

            assertEquals(Boolean.TRUE, result.get("isError"));
            assertEquals("Invalid arguments: prompt is required", response.get("error").asText());
        }
    }

    @Test
    void testCancelRaceWithUnresponsiveAgent() throws Exception {
        try (ProviderContext context = newProvider("unresponsive", 10)) {
            // Start task in background
            JsonNode started = responsePayload(call(context.provider(), "start_task", objectNode().put("prompt", "Test prompt")));
            String taskId = started.get("taskId").asText();
            
            // Immediately cancel - should prevent hanging on createSession
            Thread.sleep(50); // Small delay to ensure task execution has started
            Map<String, Object> cancelResult = call(context.provider(), "cancel_task", objectNode().put("taskId", taskId));
            JsonNode cancelResponse = responsePayload(cancelResult);
            
            assertEquals(Boolean.FALSE, cancelResult.get("isError"));
            assertEquals("CANCELLED", cancelResponse.get("state").asText());
            assertTrue(cancelResponse.get("cancellationRequested").asBoolean());
            
            // Wait a bit to ensure task doesn't hang
            Thread.sleep(500);
            
            // Verify task is still cancelled and process was destroyed
            Map<String, Object> statusResult = call(context.provider(), "get_task_status", objectNode().put("taskId", taskId));
            JsonNode statusResponse = responsePayload(statusResult);
            assertEquals("CANCELLED", statusResponse.get("state").asText());
        }
    }

    @Test
    void testCancelAlreadyCompletedTaskIsIdempotent() throws Exception {
        try (ProviderContext context = newProvider("success")) {
            JsonNode started = responsePayload(call(context.provider(), "run_task", objectNode().put("prompt", "Test prompt")));

            Map<String, Object> result = call(context.provider(), "cancel_task", objectNode().put("taskId", started.get("taskId").asText()));
            JsonNode response = responsePayload(result);

            assertEquals(Boolean.FALSE, result.get("isError"));
            assertEquals("COMPLETED", response.get("state").asText());
            assertFalse(response.get("cancellationRequested").asBoolean());
            assertNotNull(response.get("message"));
        }
    }

    private static ProviderContext newProvider(String behavior) throws IOException {
        return newProvider(behavior, 5);
    }

    private static ProviderContext newProvider(String behavior, int timeoutSeconds) throws IOException {
        FakeAcpAgent agent = new FakeAcpAgent();
        agent.setBehavior(behavior);
        agent.start();

        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "copilot", "copilot/", "agent", List.of("public"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration(timeoutSeconds, 1000, 12000, 24000)
        );
        AgentTaskRepository repository = new InMemoryAgentTaskRepository();
        Vertx vertx = Vertx.vertx();
        AcpProcessLauncher launcher = new AcpProcessLauncher() {
            @Override
            public AcpProcessHandle launch(AcpProcessConfiguration ignored) {
                return agent.processHandle();
            }
        };
        AcpVirtualToolProvider provider = new AcpVirtualToolProvider(vertx, config, repository, launcher, new AcpAuditLogger(), new AcpEventMapper());
        return new ProviderContext(provider, repository, vertx, agent);
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

    private record ProviderContext(AcpVirtualToolProvider provider, AgentTaskRepository repository, Vertx vertx, FakeAcpAgent agent) implements AutoCloseable {
        @Override
        public void close() {
            agent.close();
            vertx.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        }
    }
}
