package com.aresstack.pyloros;

import com.aresstack.pyloros.acp.registry.AcpRegistryCache;
import com.aresstack.pyloros.acp.registry.AcpRegistryClient;
import com.aresstack.pyloros.acp.registry.AcpRegistryManagementToolProvider;
import com.aresstack.pyloros.acp.registry.AgentLifecycleService;
import com.aresstack.pyloros.acp.registry.DefaultAgentInstaller;
import com.aresstack.pyloros.acp.registry.DistributionResolver;
import com.aresstack.pyloros.acp.registry.InstalledAgentStore;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpRegistryManagementToolIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void managementProviderExposesRequiredToolsThroughCatalog() {
        AcpRegistryCache registryCache = new AcpRegistryCache(tempDir.resolve("acp-registry-cache.json"));
        AcpRegistryClient registryClient = new AcpRegistryClient(AcpRegistryClient.defaultRegistryUrl(), registryCache);
        InstalledAgentStore store = new InstalledAgentStore(tempDir);
        AgentLifecycleService lifecycleService = new AgentLifecycleService(
                store,
                new DistributionResolver(),
                new DefaultAgentInstaller(tempDir.resolve("agents"))
        );
        AcpRegistryManagementToolProvider provider = new AcpRegistryManagementToolProvider(
                registryClient,
                lifecycleService,
                store
        );

        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(provider)));
        Set<String> toolNames = toolCatalog.listTools(ToolView.PUBLIC)
                .toCompletionStage()
                .toCompletableFuture()
                .join()
                .stream()
                .map(tool -> (String) tool.get("name"))
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "acp_registry/list_agents",
                "acp_registry/refresh_registry",
                "acp_registry/install_agent",
                "acp_registry/update_agent",
                "acp_registry/uninstall_agent",
                "acp_registry/configure_agent",
                "acp_registry/list_installed_agents",
                "acp_registry/get_agent_status"
        ), toolNames);
        assertTrue(toolCatalog.findByExternalName("acp_registry/list_agents", ToolView.PUBLIC).isPresent());
    }
}
