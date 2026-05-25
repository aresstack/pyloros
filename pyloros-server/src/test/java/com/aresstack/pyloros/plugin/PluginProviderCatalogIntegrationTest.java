package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginProviderCatalogIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void pluginProviderIsRegisteredInProviderRegistry() {
        PluginRegistry pluginRegistry = loadPlugins(Set.of(), new PluginWithProvider("plugin-one", provider("plugin-tools")));

        ProviderRegistry providerRegistry = new ProviderRegistry(pluginRegistry.contributedProviders());

        assertTrue(providerRegistry.findById("plugin-tools").isPresent());
        assertEquals(ProviderType.MCP,
                providerRegistry.findDescriptorById("plugin-tools").orElseThrow().providerType());
    }

    @Test
    void pluginToolIsVisibleInPublicView() {
        PluginRegistry pluginRegistry = loadPlugins(Set.of(), new PluginWithProvider("plugin-one", provider("plugin-tools")));
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(pluginRegistry.contributedProviders()));

        List<String> toolNames = await(toolCatalog.listTools(ToolView.PUBLIC)).stream()
                .map(tool -> (String) tool.get("name"))
                .toList();

        assertTrue(toolNames.contains("plugin-tools__plugin_echo"));
    }

    @Test
    void pluginToolIsHiddenInNonExposedView() {
        PluginRegistry pluginRegistry = loadPlugins(Set.of(), new PluginWithProvider("plugin-one", provider("plugin-tools")));
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(pluginRegistry.contributedProviders()));

        List<String> toolNames = await(toolCatalog.listTools(ToolView.AGENT)).stream()
                .map(tool -> (String) tool.get("name"))
                .toList();

        assertFalse(toolNames.contains("plugin-tools__plugin_echo"));
    }

    @Test
    void nameCollisionBetweenNativeAndPluginProviderFailsCatalogValidation() {
        ToolProvider nativeProvider = provider("native-tools", true);
        ToolProvider pluginProvider = provider("plugin-tools", true);
        PluginRegistry pluginRegistry = loadPlugins(Set.of(), new PluginWithProvider("plugin-one", pluginProvider));

        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(nativeProvider, pluginRegistry.contributedProviders().getFirst())));

        CompletionException thrown = assertThrows(CompletionException.class, () -> await(toolCatalog.listTools(ToolView.PUBLIC)));

        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("Duplicate external tool name: plugin_echo", thrown.getCause().getMessage());
    }

    @Test
    void pluginToolCallRunsThroughToolRouter() {
        RecordingProvider pluginProvider = provider("plugin-tools");
        PluginRegistry pluginRegistry = loadPlugins(Set.of(), new PluginWithProvider("plugin-one", pluginProvider));
        ProviderRegistry providerRegistry = new ProviderRegistry(pluginRegistry.contributedProviders());
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);
        JsonNode arguments = JSON.createObjectNode().put("value", "ok");

        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("plugin-tools__plugin_echo", arguments)));

        assertEquals("plugin_echo", pluginProvider.lastUpstreamToolName);
        assertEquals(arguments, pluginProvider.lastArguments);
        assertFalse(Boolean.TRUE.equals(result.get("isError")));
    }

    @Test
    void disabledPluginDoesNotContributeToolsToCatalog() {
        PluginRegistry pluginRegistry = loadPlugins(Set.of("plugin-one"), new PluginWithProvider("plugin-one", provider("plugin-tools")));
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(pluginRegistry.contributedProviders()));

        List<Map<String, Object>> tools = await(toolCatalog.listTools(ToolView.PUBLIC));

        assertTrue(tools.isEmpty());
    }

    @Test
    void disabledPluginDoesNotHideExistingNativeTool() {
        PluginRegistry pluginRegistry = loadPlugins(Set.of("plugin-one"), new PluginWithProvider("plugin-one", provider("plugin-tools")));
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(new PylorosPingToolProvider())));

        List<String> toolNames = await(toolCatalog.listTools(ToolView.PUBLIC)).stream()
                .map(tool -> (String) tool.get("name"))
                .toList();

        assertTrue(pluginRegistry.contributedProviders().isEmpty());
        assertTrue(toolNames.contains("pyloros__ping"));
    }

    @Test
    void invalidPluginContributionIsIsolatedFromExistingProviderAndRouter() {
        RecordingProvider existingProvider = provider("github");
        PluginRegistry pluginRegistry = loadPlugins(Set.of(), new InvalidContributionPlugin());
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(existingProvider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        List<String> toolNames = await(toolCatalog.listTools(ToolView.PUBLIC)).stream()
                .map(tool -> (String) tool.get("name"))
                .toList();
        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("github__plugin_echo", JSON.createObjectNode())));

        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, pluginRegistry.results().getFirst().status());
        assertTrue(pluginRegistry.contributedProviders().isEmpty());
        assertTrue(toolNames.contains("github__plugin_echo"));
        assertFalse(Boolean.TRUE.equals(result.get("isError")));
    }

    private static PluginRegistry loadPlugins(Set<String> disabledPluginIds, PylorosPlugin... plugins) {
        List<ServiceLoader.Provider<? extends PylorosPlugin>> entries = new ArrayList<>();
        for (PylorosPlugin plugin : plugins) {
            entries.add(candidate(plugin.getClass(), () -> plugin));
        }
        return PluginRegistry.loadFrom(entries, disabledPluginIds);
    }

    private static RecordingProvider provider(String providerId) {
        return provider(providerId, false);
    }

    private static RecordingProvider provider(String providerId, boolean preservesUpstreamToolName) {
        return new RecordingProvider(providerId, preservesUpstreamToolName);
    }

    private static <P extends PylorosPlugin> ServiceLoader.Provider<P> candidate(Class<? extends P> type, Supplier<P> factory) {
        return new ServiceLoader.Provider<>() {
            @Override
            public Class<? extends P> type() {
                return type;
            }

            @Override
            public P get() {
                return factory.get();
            }
        };
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static final class PluginWithProvider implements PylorosPlugin {
        private final String pluginId;
        private final ToolProvider provider;

        private PluginWithProvider(String pluginId, ToolProvider provider) {
            this.pluginId = pluginId;
            this.provider = provider;
        }

        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of(pluginId);
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            return PluginContribution.ofToolProviders(provider);
        }
    }

    private static final class RecordingProvider implements ToolProvider {
        private final String providerId;
        private final boolean preservesUpstreamToolName;
        private String lastUpstreamToolName;
        private JsonNode lastArguments;

        private RecordingProvider(String providerId, boolean preservesUpstreamToolName) {
            this.providerId = providerId;
            this.preservesUpstreamToolName = preservesUpstreamToolName;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.MCP;
        }

        @Override
        public List<ToolView> exposedViews() {
            return List.of(ToolView.PUBLIC);
        }

        @Override
        public boolean preservesUpstreamToolName() {
            return preservesUpstreamToolName;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(List.of(Map.of(
                    "name", "plugin_echo",
                    "description", "Plugin echo tool",
                    "inputSchema", Map.of("type", "object")
            )));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            this.lastUpstreamToolName = upstreamToolName;
            this.lastArguments = arguments;
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "ok")),
                    "isError", false
            ));
        }

    }

    private static final class InvalidContributionPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("invalid-contribution");
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            return PluginContribution.ofToolProviders(provider("   "));
        }
    }
}
