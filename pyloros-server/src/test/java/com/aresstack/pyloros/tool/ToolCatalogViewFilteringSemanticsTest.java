package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCatalogViewFilteringSemanticsTest {

    @Test
    void providerViewGateRespectsProviderListToolsFilteringAcrossTypes() {
        ToolProvider nativeProvider = new ViewAwareProvider("native-provider", ProviderType.NATIVE, List.of(ToolView.PUBLIC, ToolView.AGENT));
        ToolProvider mcpProvider = new ViewAwareProvider("mcp-provider", ProviderType.MCP, List.of(ToolView.PUBLIC, ToolView.AGENT));
        ToolProvider acpProvider = new ViewAwareProvider("acp-provider", ProviderType.ACP, List.of(ToolView.PUBLIC, ToolView.AGENT));

        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(nativeProvider, mcpProvider, acpProvider)));

        Set<String> publicNames = toolNames(await(toolCatalog.listTools(ToolView.PUBLIC)));
        Set<String> agentNames = toolNames(await(toolCatalog.listTools(ToolView.AGENT)));

        assertTrue(publicNames.contains("native-provider__native_provider_public"));
        assertTrue(publicNames.contains("mcp-provider__mcp_provider_public"));
        assertTrue(publicNames.contains("acp-provider__acp_provider_public"));

        assertTrue(agentNames.contains("native-provider__native_provider_agent"));
        assertTrue(agentNames.contains("mcp-provider__mcp_provider_agent"));
        assertTrue(agentNames.contains("acp-provider__acp_provider_agent"));
    }

    @Test
    void providerExcludedFromViewStaysHiddenEvenIfProviderReturnsThatViewTools() {
        ToolProvider pluginLikeProvider = new ViewAwareProvider("plugin-tools", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(pluginLikeProvider)));

        Set<String> publicNames = toolNames(await(toolCatalog.listTools(ToolView.PUBLIC)));
        Set<String> agentNames = toolNames(await(toolCatalog.listTools(ToolView.AGENT)));

        assertTrue(publicNames.contains("plugin-tools__plugin_tools_public"));
        assertFalse(agentNames.contains("plugin-tools__plugin_tools_agent"));
        assertEquals(List.of("plugin-tools__plugin_tools_public"), publicNames.stream().toList());
    }

    private static Set<String> toolNames(List<Map<String, Object>> tools) {
        return tools.stream().map(tool -> (String) tool.get("name")).collect(java.util.stream.Collectors.toSet());
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static final class ViewAwareProvider implements ToolProvider {
        private final String providerId;
        private final ProviderType providerType;
        private final List<ToolView> exposedViews;

        private ViewAwareProvider(String providerId, ProviderType providerType, List<ToolView> exposedViews) {
            this.providerId = providerId;
            this.providerType = providerType;
            this.exposedViews = exposedViews;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public ProviderType providerType() {
            return providerType;
        }

        @Override
        public List<ToolView> exposedViews() {
            return exposedViews;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools(ToolView toolView) {
            String normalizedProvider = providerId.replace('-', '_');
            String toolName = normalizedProvider + "_" + toolView.name().replace('-', '_');
            return Future.succeededFuture(List.of(Map.of(
                    "name", toolName,
                    "description", providerId + ":" + toolView.name(),
                    "inputSchema", Map.of("type", "object")
            )));
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return listTools(ToolView.PUBLIC);
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            return Future.succeededFuture(Map.of("isError", false));
        }
    }
}
