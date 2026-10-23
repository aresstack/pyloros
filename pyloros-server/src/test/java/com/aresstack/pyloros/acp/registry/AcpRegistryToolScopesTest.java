package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Installing or configuring an ACP agent changes which executables Pyloros runs on
 * the host, so those tools demand an administrative scope while the listing tools do
 * not.
 *
 * <p>Without this, a token carrying only the gateway scope could install and run an
 * arbitrary agent from the registry.
 */
class AcpRegistryToolScopesTest {

    @TempDir Path tempDir;

    private AcpRegistryManagementToolProvider provider;
    private ToolRouter enforcing;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryAgentStore store = new InMemoryAgentStore();
        AcpRegistryClient registryClient = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("registry-cache.json")));
        AgentLifecycleService lifecycle = new AgentLifecycleService(
                store, new DistributionResolver(), new NoOpInstaller());
        provider = new AcpRegistryManagementToolProvider(registryClient, lifecycle, store);

        ProviderRegistry registry = new ProviderRegistry(List.of(provider));
        ToolCatalog catalog = new ToolCatalog(registry, ToolNameFormatter.defaultFormatter());
        enforcing = new ToolRouter(registry, catalog, true);
        await(catalog.refreshForView(ToolView.PUBLIC));
    }

    @Test
    void theMutatingRegistryToolsDemandTheAdminScope() {
        assertEqualsScope("pyloros:admin", "install_agent");
        assertEqualsScope("pyloros:admin", "update_agent");
        assertEqualsScope("pyloros:admin", "uninstall_agent");
        assertEqualsScope("pyloros:admin", "configure_agent");
    }

    @Test
    void theReadOnlyRegistryToolsDemandNothingExtra() {
        // refresh_registry only reloads the cache from the configured upstream URL.
        assertNull(provider.requiredScope("list_agents"));
        assertNull(provider.requiredScope("list_installed_agents"));
        assertNull(provider.requiredScope("get_agent_status"));
        assertNull(provider.requiredScope("refresh_registry"));
        assertNull(provider.requiredScope(null));
    }

    @Test
    void installingAnAgentIsDeniedWithTheGatewayScopeAlone() throws Exception {
        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall("acp_registry__install_agent", null),
                ToolView.PUBLIC, List.of("mcp")));

        assertTrue((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("pyloros:admin"),
                "the refusal must name the missing scope: " + result);
    }

    @Test
    void installingAnAgentPassesTheGateWithTheAdminScope() throws Exception {
        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall("acp_registry__install_agent", null),
                ToolView.PUBLIC, List.of("mcp", "pyloros:admin")));

        // Reached the tool, which then rejected the missing agent_id itself.
        assertTrue((Boolean) result.get("isError"));
        assertFalse(result.toString().contains("Permission denied"),
                "the scope check must have passed: " + result);
        assertTrue(result.toString().contains("agent_id"),
                "the tool itself must have validated the arguments: " + result);
    }

    @Test
    void listingAgentsWorksWithTheGatewayScopeAlone() throws Exception {
        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall("acp_registry__list_installed_agents", null),
                ToolView.PUBLIC, List.of("mcp")));

        assertFalse(result.toString().contains("Permission denied"),
                "a read-only tool must not require more than the gateway scope: " + result);
    }

    private void assertEqualsScope(String expected, String tool) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, provider.requiredScope(tool));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /** The repo keeps these stubs local to each ACP test; copied for the same reason. */
    private static final class InMemoryAgentStore implements AgentStoreOperations {
        private final java.util.Map<String, InstalledAgent> agents = new java.util.HashMap<>();

        @Override
        public synchronized List<InstalledAgent> listAll() {
            return List.copyOf(agents.values());
        }

        @Override
        public synchronized List<InstalledAgent> listEnabled() {
            return agents.values().stream().filter(InstalledAgent::enabled).toList();
        }

        @Override
        public synchronized java.util.Optional<InstalledAgent> findById(String agentId) {
            return java.util.Optional.ofNullable(agents.get(agentId));
        }

        @Override
        public synchronized InstalledAgent save(InstalledAgent agent) {
            agents.put(agent.agentId(), agent);
            return agent;
        }

        @Override
        public synchronized java.util.Optional<InstalledAgent> remove(String agentId) {
            return java.util.Optional.ofNullable(agents.remove(agentId));
        }

        @Override
        public synchronized java.util.Optional<InstalledAgent> setEnabled(
                String agentId, boolean enabled) {
            InstalledAgent existing = agents.get(agentId);
            if (existing == null) {
                return java.util.Optional.empty();
            }
            InstalledAgent updated = existing.withEnabled(enabled);
            agents.put(agentId, updated);
            return java.util.Optional.of(updated);
        }
    }

    private static final class NoOpInstaller implements AgentInstaller {
        @Override
        public void materialize(InstallPlan plan) {
        }

        @Override
        public void remove(InstallPlan plan) {
        }
    }
}
