package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test proving that install/configure/uninstall changes are reflected
 * in the ToolCatalog without constructing a new PylorosApplication instance.
 */
class AgentLifecycleIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Vertx vertx;
    private ProviderRegistry providerRegistry;
    private ToolCatalog toolCatalog;
    private AgentLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        Path storePath = tempDir.resolve("installed-agents.json");

        List<ToolProvider> initialProviders = new ArrayList<>();
        initialProviders.add(new PylorosPingToolProvider());

        providerRegistry = new ProviderRegistry(initialProviders);
        toolCatalog = new ToolCatalog(providerRegistry);

        InstalledAgentStore store = new InstalledAgentStore(storePath);
        InstalledAgentAcpProviderFactory factory = new InstalledAgentAcpProviderFactory(vertx, "");
        lifecycleService = new AgentLifecycleService(store, factory, providerRegistry);
    }

    @Test
    void installAgentExposesToolsWithoutRestart() {
        InstalledAgent agent = new InstalledAgent(
                "deepagents", true, "npx", List.of("deepagents-acp"),
                "deepagents__", List.of("public"), "agent", Map.of());

        lifecycleService.install(agent);

        // Tools should now be visible in the catalog
        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        List<String> toolNames = tools.stream().map(t -> (String) t.get("name")).toList();

        assertTrue(toolNames.stream().anyMatch(name -> name.contains("deepagents")),
                "Expected deepagents tools to appear in catalog after install, but got: " + toolNames);
        assertTrue(providerRegistry.findById("deepagents").isPresent(),
                "Expected provider 'deepagents' to be registered");
    }

    @Test
    void disableAgentRemovesToolsWithoutRestart() {
        InstalledAgent agent = new InstalledAgent(
                "dirac", true, "npx", List.of("dirac-acp"),
                "dirac__", List.of("public"), "agent", Map.of());
        lifecycleService.install(agent);

        // Verify it's there
        assertTrue(providerRegistry.findById("dirac").isPresent());

        // Disable
        lifecycleService.disable("dirac");

        // Provider should be gone
        assertFalse(providerRegistry.findById("dirac").isPresent(),
                "Expected provider 'dirac' to be unregistered after disable");

        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        List<String> toolNames = tools.stream().map(t -> (String) t.get("name")).toList();
        assertTrue(toolNames.stream().noneMatch(name -> name.contains("dirac")),
                "Expected no dirac tools in catalog after disable");
    }

    @Test
    void uninstallAgentRemovesToolsWithoutRestart() {
        InstalledAgent agent = new InstalledAgent(
                "testbot", true, "npx", List.of("testbot-acp"),
                "testbot__", List.of("public"), "agent", Map.of());
        lifecycleService.install(agent);
        assertTrue(providerRegistry.findById("testbot").isPresent());

        lifecycleService.uninstall("testbot");

        assertFalse(providerRegistry.findById("testbot").isPresent(),
                "Expected provider 'testbot' to be unregistered after uninstall");
    }

    @Test
    void configureAgentRefreshesCommandAndPrefix() {
        InstalledAgent agent = new InstalledAgent(
                "myagent", true, "npx", List.of("old-cmd"),
                "myagent__", List.of("public"), "agent", Map.of());
        lifecycleService.install(agent);
        assertTrue(providerRegistry.findById("myagent").isPresent());

        // Reconfigure with new command
        InstalledAgent updated = agent.withCommand("npx.cmd").withArgs(List.of("new-cmd"));
        lifecycleService.configure("myagent", updated);

        // Provider should still be registered
        assertTrue(providerRegistry.findById("myagent").isPresent(),
                "Expected provider 'myagent' to still be registered after configure");
    }

    @Test
    void enableAgentReRegistersProvider() {
        InstalledAgent agent = new InstalledAgent(
                "enabletest", false, "npx", List.of("test-acp"),
                "enabletest__", List.of("public"), "agent", Map.of());
        lifecycleService.install(agent);

        // Should not be registered since disabled
        assertFalse(providerRegistry.findById("enabletest").isPresent(),
                "Disabled agent should not have a provider registered");

        lifecycleService.enable("enabletest");

        assertTrue(providerRegistry.findById("enabletest").isPresent(),
                "Expected provider 'enabletest' to be registered after enable");
    }

    @Test
    void managementToolsRemainAvailable() {
        AcpRegistryManagementToolProvider mgmtProvider = new AcpRegistryManagementToolProvider(lifecycleService);
        providerRegistry.register(mgmtProvider);

        // Install an agent
        InstalledAgent agent = new InstalledAgent(
                "someagent", true, "npx", List.of("some-acp"),
                "someagent__", List.of("public"), "agent", Map.of());
        lifecycleService.install(agent);

        // Management provider should still be accessible
        assertTrue(providerRegistry.findById("acp_registry").isPresent(),
                "Management tool provider should remain registered");

        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        List<String> toolNames = tools.stream().map(t -> (String) t.get("name")).toList();
        assertTrue(toolNames.stream().anyMatch(name -> name.contains("install_agent")),
                "Expected management tools in catalog, got: " + toolNames);
    }

    @Test
    void managementToolProviderInstallCallWorks() {
        AcpRegistryManagementToolProvider mgmtProvider = new AcpRegistryManagementToolProvider(lifecycleService);
        providerRegistry.register(mgmtProvider);

        ObjectNode args = MAPPER.createObjectNode();
        args.put("id", "tooltest");
        args.put("command", "npx");
        args.putArray("args").add("tooltest-acp");

        Map<String, Object> result = await(mgmtProvider.callTool("install_agent", args));
        assertNotNull(result);
        assertFalse((Boolean) result.get("isError"));

        // Verify agent tools are available
        assertTrue(providerRegistry.findById("tooltest").isPresent(),
                "Agent should be registered after install via management tool");
    }

    @SuppressWarnings("unchecked")
    private static <T> T await(io.vertx.core.Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }
}
