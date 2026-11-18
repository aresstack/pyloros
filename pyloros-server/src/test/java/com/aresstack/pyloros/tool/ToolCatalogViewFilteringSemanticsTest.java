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

    @Test
    void managedAgentViewExposesPublicMcpToolsButNotAgentOrAdministrativeTools() {
        ToolView managedView = ToolView.named("antigravity-acp-agent-view");
        ToolProvider idea = new ViewAwareProvider("intellij", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolProvider github = new ViewAwareProvider("github", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolProvider agent = new ViewAwareProvider("antigravity-acp", ProviderType.ACP, List.of(ToolView.PUBLIC));
        ToolProvider management = new ViewAwareProvider("acp_registry", ProviderType.NATIVE, List.of(ToolView.PUBLIC));
        ToolProvider unknown = new ViewAwareProvider("unknown-provider", ProviderType.UNKNOWN, List.of(ToolView.PUBLIC));

        ToolCatalog toolCatalog = new ToolCatalog(
                new ProviderRegistry(List.of(idea, github, agent, management, unknown)),
                ToolNameFormatter.defaultFormatter(),
                Set.of(managedView)
        );

        assertEquals(Set.of("intellij__intellij_public", "github__github_public"),
                toolNames(await(toolCatalog.listTools(managedView))));
        assertTrue(await(toolCatalog.listTools(ToolView.named("unregistered-agent-view"))).isEmpty());
        assertTrue(toolNames(await(toolCatalog.listTools(ToolView.PUBLIC)))
                .contains("antigravity-acp__antigravity_acp_public"));
    }

    @Test
    void selectedAgentToolsAreEagerAndDoNotChangePublicToolCatalog() {
        ToolView managedView = ToolView.named("antigravity-acp-agent-view");
        ToolProvider idea = new ViewAwareProvider("intellij", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolProvider github = new ViewAwareProvider("github", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolCatalog catalog = new ToolCatalog(
                new ProviderRegistry(List.of(idea, github)),
                ToolNameFormatter.defaultFormatter(),
                Set.of(managedView),
                Set.of("intellij__intellij_public"));

        List<Map<String, Object>> privateTools = await(catalog.listTools(managedView));

        assertEquals(1, privateTools.size());
        assertEquals("intellij__intellij_public", privateTools.get(0).get("name"));
        assertEquals(Boolean.TRUE,
                ((Map<?, ?>) privateTools.get(0).get("_meta")).get("antigravity/alwaysLoad"));

        List<Map<String, Object>> publicTools = await(catalog.listTools(ToolView.PUBLIC));
        assertEquals(2, publicTools.size());
        assertTrue(publicTools.stream().noneMatch(tool -> tool.containsKey("_meta")));
    }

    @Test
    void eagerSelectionDoesNotRestrictOtherManagedAcpAgentViews() {
        ToolView antigravity = ToolView.named("antigravity-acp-agent-view");
        ToolView gemini = ToolView.named("gemini-agent-view");
        ToolProvider idea = new ViewAwareProvider("intellij", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolProvider github = new ViewAwareProvider("github", ProviderType.MCP, List.of(ToolView.PUBLIC));
        ToolCatalog catalog = new ToolCatalog(
                new ProviderRegistry(List.of(idea, github)),
                ToolNameFormatter.defaultFormatter(),
                Set.of(antigravity, gemini),
                Set.of("intellij__intellij_public"),
                Set.of(antigravity));

        assertEquals(Set.of("intellij__intellij_public"),
                toolNames(await(catalog.listTools(antigravity))));

        List<Map<String, Object>> geminiTools = await(catalog.listTools(gemini));

        assertEquals(Set.of("intellij__intellij_public", "github__github_public"),
                toolNames(geminiTools));
        assertTrue(geminiTools.stream().noneMatch(tool -> tool.containsKey("_meta")));
    }

    @Test
    void eagerAgentCatalogDoesNotWaitForUnrelatedUnreachableProviders() {
        ToolView agentView = ToolView.named("antigravity-acp-agent-view");
        ToolProvider idea = new ViewAwareProvider("intellij", ProviderType.MCP, List.of(ToolView.PUBLIC));
        java.util.concurrent.atomic.AtomicBoolean unrelatedCalled = new java.util.concurrent.atomic.AtomicBoolean();
        ToolProvider unrelated = new ToolProvider() {
            @Override
            public String providerId() {
                return "unrelated";
            }

            @Override
            public ProviderType providerType() {
                return ProviderType.MCP;
            }

            @Override
            public Future<List<Map<String, Object>>> listTools() {
                unrelatedCalled.set(true);
                return Future.failedFuture("Unreachable MCP server");
            }

            @Override
            public Future<Map<String, Object>> callTool(String toolName, JsonNode arguments) {
                return Future.failedFuture("Unavailable");
            }
        };
        ToolCatalog catalog = new ToolCatalog(
                new ProviderRegistry(List.of(idea, unrelated)),
                ToolNameFormatter.defaultFormatter(),
                Set.of(agentView),
                Set.of("intellij__intellij_public"),
                Set.of(agentView));

        assertEquals(Set.of("intellij__intellij_public"),
                toolNames(await(catalog.listTools(agentView))));
        assertFalse(unrelatedCalled.get());
    }

    @Test
    void publicViewCannotBeRegisteredAsManagedAgentView() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ToolCatalog(new ProviderRegistry(List.of()),
                        ToolNameFormatter.defaultFormatter(), Set.of(ToolView.PUBLIC)));
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
