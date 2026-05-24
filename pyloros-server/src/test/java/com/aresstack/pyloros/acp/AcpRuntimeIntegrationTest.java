package com.aresstack.pyloros.acp;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * Verifies the full runtime path: AcpProviderConfiguration → AcpVirtualToolProvider
 * → ProviderRegistry → ToolCatalog → ToolRouter.
 * Uses FakeAcpAgent over piped streams to validate tool dispatch end-to-end.
 */
class AcpRuntimeIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Vertx vertx;

    @BeforeAll
    static void setup() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void teardown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void acpProviderRegistersToolsInCatalogAndRoutesCorrectly() throws IOException {
        FakeAcpAgent agent = new FakeAcpAgent();
        agent.setBehavior("success");
        agent.start();

        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "copilot",
                "copilot/",
                "agent",
                List.of("public"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration(5, 100, 12000, 24000)
        );

        AgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AcpProcessLauncher launcher = new AcpProcessLauncher() {
            @Override
            public AcpProcessHandle launch(AcpProcessConfiguration ignored) {
                return agent.processHandle();
            }
        };

        AcpVirtualToolProvider acpProvider = new AcpVirtualToolProvider(
                vertx, config, repository, launcher, new AcpAuditLogger(), new AcpEventMapper());

        // Register in ProviderRegistry alongside the ACP provider
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(acpProvider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, new ToolNameFormatter("/"));
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        // Verify tools/list shows all ACP tools via the catalog
        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        Set<String> toolNames = extractToolNames(tools);

        assertTrue(toolNames.contains("copilot/run_task"), "Expected copilot/run_task in tools/list");
        assertTrue(toolNames.contains("copilot/start_task"), "Expected copilot/start_task in tools/list");
        assertTrue(toolNames.contains("copilot/get_task_status"), "Expected copilot/get_task_status in tools/list");
        assertTrue(toolNames.contains("copilot/get_task_result"), "Expected copilot/get_task_result in tools/list");
        assertTrue(toolNames.contains("copilot/cancel_task"), "Expected copilot/cancel_task in tools/list");

        // Verify ToolRouter dispatches run_task correctly to ACP provider
        ObjectNode runArgs = JSON.createObjectNode().put("prompt", "Hello from runtime test");
        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("copilot/run_task", runArgs)));

        assertNotNull(result);
        assertFalse((Boolean) result.getOrDefault("isError", false), "run_task should succeed");

        agent.close();
    }

    @Test
    void acpProviderToolsNotVisibleForUnknownToolNames() throws IOException {
        FakeAcpAgent agent = new FakeAcpAgent();
        agent.setBehavior("success");
        agent.start();

        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "copilot",
                "copilot/",
                "agent",
                List.of("public"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration(5, 100, 12000, 24000)
        );

        AgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AcpProcessLauncher launcher = new AcpProcessLauncher() {
            @Override
            public AcpProcessHandle launch(AcpProcessConfiguration ignored) {
                return agent.processHandle();
            }
        };

        AcpVirtualToolProvider acpProvider = new AcpVirtualToolProvider(
                vertx, config, repository, launcher, new AcpAuditLogger(), new AcpEventMapper());

        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(acpProvider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, new ToolNameFormatter("/"));
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);
        await(toolCatalog.listTools());

        // Unknown tool returns error
        Map<String, Object> result = await(toolRouter.callTool(
                new McpToolCall("copilot/nonexistent", JSON.createObjectNode())));

        assertEquals(Boolean.TRUE, result.get("isError"));

        agent.close();
    }

    @Test
    void startTaskThenGetStatusViaRouter() throws IOException {
        FakeAcpAgent agent = new FakeAcpAgent();
        agent.setBehavior("success");
        agent.start();

        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "copilot",
                "copilot/",
                "agent",
                List.of("public"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration(5, 100, 12000, 24000)
        );

        AgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AcpProcessLauncher launcher = new AcpProcessLauncher() {
            @Override
            public AcpProcessHandle launch(AcpProcessConfiguration ignored) {
                return agent.processHandle();
            }
        };

        AcpVirtualToolProvider acpProvider = new AcpVirtualToolProvider(
                vertx, config, repository, launcher, new AcpAuditLogger(), new AcpEventMapper());

        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(acpProvider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, new ToolNameFormatter("/"));
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);
        await(toolCatalog.listTools());

        // start_task via router
        ObjectNode startArgs = JSON.createObjectNode().put("prompt", "Async task");
        Map<String, Object> startResult = await(toolRouter.callTool(new McpToolCall("copilot/start_task", startArgs)));
        assertFalse((Boolean) startResult.getOrDefault("isError", false));

        // Extract taskId from result
        String taskId = extractTaskId(startResult);
        assertNotNull(taskId, "start_task should return a taskId");

        // Wait a moment for the background task to complete
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        // get_task_status via router
        ObjectNode statusArgs = JSON.createObjectNode().put("taskId", taskId);
        Map<String, Object> statusResult = await(toolRouter.callTool(new McpToolCall("copilot/get_task_status", statusArgs)));
        assertNotNull(statusResult);
        assertFalse((Boolean) statusResult.getOrDefault("isError", false));

        agent.close();
    }

    private static Set<String> extractToolNames(List<Map<String, Object>> tools) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> tool : tools) {
            names.add((String) tool.get("name"));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static String extractTaskId(Map<String, Object> result) {
        Object content = result.get("content");
        if (!(content instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.getFirst();
        if (!(first instanceof Map<?, ?> item)) {
            return null;
        }
        Object text = item.get("text");
        if (text == null) {
            return null;
        }
        try {
            Map<String, Object> parsed = JSON.readValue(text.toString(), Map.class);
            Object taskId = parsed.get("taskId");
            return taskId != null ? taskId.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }
}
