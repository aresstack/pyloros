package com.aresstack.pyloros.provider;

import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test proving that dynamic register/unregister on ProviderRegistry
 * is reflected in ToolCatalog.listTools() without constructing a new application instance.
 */
class ProviderRegistryDynamicRefreshTest {

    private ProviderRegistry providerRegistry;
    private ToolCatalog toolCatalog;

    @BeforeEach
    void setUp() {
        providerRegistry = new ProviderRegistry(List.of(new PylorosPingToolProvider()));
        toolCatalog = new ToolCatalog(providerRegistry);
    }

    @Test
    void registerProviderExposesToolsInCatalogWithoutRestart() {
        FakeToolProvider fakeProvider = new FakeToolProvider("agentx", List.of(
                Map.of("name", "run_task", "description", "Run a task")
        ));

        providerRegistry.register(fakeProvider);

        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        List<String> toolNames = tools.stream().map(t -> (String) t.get("name")).toList();

        assertTrue(toolNames.stream().anyMatch(name -> name.contains("run_task")),
                "Expected agentx tools to appear in catalog after register, got: " + toolNames);
    }

    @Test
    void unregisterProviderRemovesToolsFromCatalogWithoutRestart() {
        FakeToolProvider fakeProvider = new FakeToolProvider("removable", List.of(
                Map.of("name", "do_something", "description", "Does something")
        ));
        providerRegistry.register(fakeProvider);

        // Verify present
        List<Map<String, Object>> toolsBefore = await(toolCatalog.listTools());
        assertTrue(toolsBefore.stream().anyMatch(t -> ((String) t.get("name")).contains("do_something")));

        // Unregister
        providerRegistry.unregister("removable");

        List<Map<String, Object>> toolsAfter = await(toolCatalog.listTools());
        assertTrue(toolsAfter.stream().noneMatch(t -> ((String) t.get("name")).contains("do_something")),
                "Expected removable tools to be gone from catalog after unregister");
    }

    @Test
    void registerReplacementProviderUpdatesToolsInCatalog() {
        FakeToolProvider original = new FakeToolProvider("myagent", List.of(
                Map.of("name", "old_tool", "description", "Old tool")
        ));
        providerRegistry.register(original);

        List<Map<String, Object>> toolsBefore = await(toolCatalog.listTools());
        assertTrue(toolsBefore.stream().anyMatch(t -> ((String) t.get("name")).contains("old_tool")));

        // Replace with new tool set
        FakeToolProvider replacement = new FakeToolProvider("myagent", List.of(
                Map.of("name", "new_tool", "description", "New tool")
        ));
        providerRegistry.register(replacement);

        List<Map<String, Object>> toolsAfter = await(toolCatalog.listTools());
        List<String> names = toolsAfter.stream().map(t -> (String) t.get("name")).toList();
        assertTrue(names.stream().anyMatch(name -> name.contains("new_tool")),
                "Expected new_tool after replacement, got: " + names);
        assertTrue(names.stream().noneMatch(name -> name.contains("old_tool")),
                "Expected old_tool to be gone after replacement, got: " + names);
    }

    @Test
    void existingProvidersRemainAfterDynamicRegisterUnregister() {
        FakeToolProvider dynamic = new FakeToolProvider("transient", List.of(
                Map.of("name", "temp", "description", "Temporary")
        ));
        providerRegistry.register(dynamic);
        providerRegistry.unregister("transient");

        // The original PylorosPingToolProvider should still be there
        assertTrue(providerRegistry.findById("pyloros").isPresent());
        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        assertTrue(tools.stream().anyMatch(t -> ((String) t.get("name")).contains("pyloros__ping")),
                "Original pyloros tools should still be available");
    }

    @SuppressWarnings("unchecked")
    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static final class FakeToolProvider implements ToolProvider {
        private final String providerId;
        private final List<Map<String, Object>> tools;

        FakeToolProvider(String providerId, List<Map<String, Object>> tools) {
            this.providerId = providerId;
            this.tools = tools;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.ACP;
        }

        @Override
        public List<ToolView> exposedViews() {
            return List.of(ToolView.PUBLIC);
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(tools);
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            return Future.succeededFuture(Map.of("isError", false, "content", List.of()));
        }
    }
}
