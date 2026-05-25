package com.aresstack.pyloros;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.plugin.PluginActivationResolver;
import com.aresstack.pyloros.plugin.PluginLoadResult;
import com.aresstack.pyloros.plugin.PluginRegistry;
import com.aresstack.pyloros.plugin.PluginStatus;
import com.aresstack.pyloros.plugin.PluginsConfig;
import com.aresstack.pyloros.plugin.PluginEntry;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleEchoPluginIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void examplePluginIsDiscoveredViaServiceLoaderWhenEnabled() {
        PluginRegistry registry = PluginRegistry.load(new PluginActivationResolver(new PluginsConfig(
                Boolean.FALSE,
                List.of(new PluginEntry("example-echo-plugin", Boolean.TRUE, Map.of()))
        )));

        PluginLoadResult plugin = registry.findById("example-echo-plugin").orElseThrow();
        assertEquals(PluginStatus.LOADED, plugin.status());
        assertTrue(registry.contributedProviders().stream()
                .anyMatch(provider -> provider.providerId().equals("example-tools")));
    }

    @Test
    void exampleEchoToolAppearsInCatalogAndCanBeCalledThroughRouter() {
        PluginRegistry registry = PluginRegistry.load(new PluginActivationResolver(new PluginsConfig(
                Boolean.FALSE,
                List.of(new PluginEntry("example-echo-plugin", Boolean.TRUE, Map.of()))
        )));

        ProviderRegistry providerRegistry = new ProviderRegistry(registry.contributedProviders());
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        List<String> toolNames = await(toolCatalog.listTools(ToolView.PUBLIC)).stream()
                .map(tool -> (String) tool.get("name"))
                .toList();
        assertTrue(toolNames.contains("example-tools__echo"));

        Map<String, Object> callResult = await(toolRouter.callTool(
                new McpToolCall("example-tools__echo", JSON.createObjectNode().put("text", "hello"))));

        assertFalse(Boolean.TRUE.equals(callResult.get("isError")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) callResult.get("content");
        assertEquals("hello", content.getFirst().get("text"));
    }

    @Test
    void explicitlyDisabledExamplePluginDoesNotExposeExampleTool() {
        PluginRegistry registry = PluginRegistry.load(new PluginActivationResolver(new PluginsConfig(
                Boolean.TRUE,
                List.of(
                        new PluginEntry("example-echo-plugin", Boolean.FALSE, Map.of()),
                        new PluginEntry("app-bootstrap-test-plugin", Boolean.FALSE, Map.of())
                )
        )));

        PluginLoadResult plugin = registry.findById("example-echo-plugin").orElseThrow();
        assertEquals(PluginStatus.DISABLED, plugin.status());
        assertFalse(registry.contributedProviders().stream()
                .anyMatch(provider -> provider.providerId().equals("example-tools")));

        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(registry.contributedProviders()));
        List<String> toolNames = await(toolCatalog.listTools(ToolView.PUBLIC)).stream()
                .map(tool -> (String) tool.get("name"))
                .toList();
        assertFalse(toolNames.contains("example-tools__echo"));
    }

    private static <T> T await(io.vertx.core.Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }
}
