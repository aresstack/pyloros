package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for R7-07: Headless management API and tools for ACP registry agents.
 */
class AcpRegistryManagementToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private AcpRegistryClient registryClient;
    private InMemoryAgentStoreOperations store;
    private AgentLifecycleService lifecycleService;
    private AcpRegistryManagementToolProvider provider;

    @BeforeEach
    void setUp() {
        store = new InMemoryAgentStoreOperations();
        registryClient = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("test-registry-cache.json")));

        DistributionResolver resolver = new DistributionResolver();
        NoOpAgentInstaller installer = new NoOpAgentInstaller();
        lifecycleService = new AgentLifecycleService(store, resolver, installer);

        provider = new AcpRegistryManagementToolProvider(registryClient, lifecycleService, store);

        // Pre-populate registry with a fake agent
        registryClient.loadFrom(new java.io.ByteArrayInputStream(FAKE_REGISTRY_JSON.getBytes()));
    }

    private static final String FAKE_REGISTRY_JSON = """
            {
              "version": "1.0.0",
              "agents": [
                {
                  "id": "echo-agent",
                  "name": "Echo Agent",
                  "version": "0.1.0",
                  "description": "A simple echo agent for testing",
                  "repository": "https://github.com/example/echo-agent",
                  "license": "MIT",
                  "distribution": {
                    "npx": { "package": "echo-agent@0.1.0" }
                  }
                },
                {
                  "id": "cli-auth-agent",
                  "name": "CLI Auth Agent",
                  "version": "1.0.0",
                  "description": "Requires terminal auth",
                  "license": "Apache-2.0",
                  "auth": {
                    "method": "TERMINAL_AUTH",
                    "setupCommand": "my-cli login --token",
                    "description": "Run once to authenticate"
                  },
                  "distribution": {
                    "npx": { "package": "cli-auth-agent@1.0.0" }
                  }
                },
                {
                  "id": "binary-tool",
                  "name": "Binary Tool",
                  "version": "2.0.0",
                  "description": "Binary distribution agent",
                  "license": "MIT",
                  "distribution": {
                    "npx": { "package": "binary-tool@2.0.0" }
                  }
                }
              ]
            }
            """;

    // -------------------------------------------------------------------
    // listTools — 8 management tools are exposed
    // -------------------------------------------------------------------

    @Test
    void listToolsReturnsEightManagementTools() {
        List<Map<String, Object>> tools = await(provider.listTools());
        assertEquals(8, tools.size());

        List<String> names = tools.stream().map(t -> (String) t.get("name")).toList();
        assertTrue(names.contains("list_agents"));
        assertTrue(names.contains("refresh_registry"));
        assertTrue(names.contains("install_agent"));
        assertTrue(names.contains("update_agent"));
        assertTrue(names.contains("uninstall_agent"));
        assertTrue(names.contains("configure_agent"));
        assertTrue(names.contains("list_installed_agents"));
        assertTrue(names.contains("get_agent_status"));
    }

    // -------------------------------------------------------------------
    // list_agents
    // -------------------------------------------------------------------

    @Test
    void listRegistryAgentsReturnsAllAgents() {
        Map<String, Object> result = callTool("list_agents", MAPPER.createObjectNode());

        assertFalse((boolean) result.get("isError"), "Should not be error");
        String text = extractText(result);
        assertTrue(text.contains("echo-agent"));
        assertTrue(text.contains("cli-auth-agent"));
        assertTrue(text.contains("binary-tool"));
        assertTrue(text.contains("totalCount"), "Should have totalCount: " + text);
    }

    @Test
    void listRegistryAgentsWithFilter() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("filter", "cli");

        Map<String, Object> result = callTool("list_agents", args);

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("cli-auth-agent"));
        assertFalse(text.contains("echo-agent"), "Filtered out agent should not appear");
    }

    @Test
    void listRegistryAgentsShowsInstalledStatus() {
        // Install an agent first
        ObjectNode installArgs = MAPPER.createObjectNode();
        installArgs.put("agent_id", "echo-agent");
        callTool("install_agent", installArgs);

        // Now list
        Map<String, Object> result = callTool("list_agents", MAPPER.createObjectNode());

        String text = extractText(result);
        assertTrue(text.contains("installed"), "Should show installed key: " + text);
        assertTrue(text.contains("0.1.0"));
    }

    // -------------------------------------------------------------------
    // refresh_registry
    // -------------------------------------------------------------------

    @Test
    void refreshRegistrySucceeds() {
        Map<String, Object> result = callTool("refresh_registry", MAPPER.createObjectNode());

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("1.0.0"));
        assertTrue(text.contains("agentCount"), "Should have agentCount: " + text);
    }

    // -------------------------------------------------------------------
    // install_agent
    // -------------------------------------------------------------------

    @Test
    void installAgentSucceeds() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "echo-agent");

        Map<String, Object> result = callTool("install_agent", args);

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("INSTALLED"));
        assertTrue(text.contains("echo-agent"));
        assertTrue(text.contains("0.1.0"));

        // Verify it's actually persisted
        assertTrue(store.findById("echo-agent").isPresent());
    }

    @Test
    void installAgentWithAuthShowsRequiredStatus() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "cli-auth-agent");

        Map<String, Object> result = callTool("install_agent", args);

        String text = extractText(result);
        assertTrue(text.contains("REQUIRED"));
        assertTrue(text.contains("TERMINAL_AUTH"));
    }

    @Test
    void installAgentAlreadyInstalledReturnsError() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "echo-agent");
        callTool("install_agent", args); // first install

        Map<String, Object> result = callTool("install_agent", args); // second attempt

        assertTrue((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("ALREADY_INSTALLED"));
    }

    @Test
    void installAgentNotFoundReturnsError() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "nonexistent-agent");

        Map<String, Object> result = callTool("install_agent", args);

        assertTrue((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("not found"));
    }

    // -------------------------------------------------------------------
    // configure_agent
    // -------------------------------------------------------------------

    @Test
    void configureAgentPrefixAndViewAndEnabled() {
        // Install first
        ObjectNode installArgs = MAPPER.createObjectNode();
        installArgs.put("agent_id", "echo-agent");
        callTool("install_agent", installArgs);

        // Configure
        ObjectNode configArgs = MAPPER.createObjectNode();
        configArgs.put("agent_id", "echo-agent");
        configArgs.put("prefix", "custom-prefix/");
        configArgs.put("agent_tool_view", "custom-view");
        configArgs.put("enabled", false);

        Map<String, Object> result = callTool("configure_agent", configArgs);

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("custom-prefix/"));
        assertTrue(text.contains("custom-view"));
        assertTrue(text.contains("false"), "Should show enabled=false: " + text);
        assertTrue(text.contains("true"), "Should show changed=true: " + text);

        // Verify persistence
        InstalledAgent agent = store.findById("echo-agent").orElseThrow();
        assertEquals("custom-prefix/", agent.configuredPrefix());
        assertEquals("custom-view", agent.agentToolView());
        assertFalse(agent.enabled());
    }

    @Test
    void configureAgentAuthStatus() {
        // Install auth agent
        ObjectNode installArgs = MAPPER.createObjectNode();
        installArgs.put("agent_id", "cli-auth-agent");
        callTool("install_agent", installArgs);

        // Configure auth to configured
        ObjectNode configArgs = MAPPER.createObjectNode();
        configArgs.put("agent_id", "cli-auth-agent");
        configArgs.put("auth_status", "CONFIGURED");

        Map<String, Object> result = callTool("configure_agent", configArgs);

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("CONFIGURED"));

        InstalledAgent agent = store.findById("cli-auth-agent").orElseThrow();
        assertEquals(AgentAuthStatus.CONFIGURED, agent.authStatus());
    }

    @Test
    void configureAgentCannotSetAuthStatusToRequired() {
        ObjectNode installArgs = MAPPER.createObjectNode();
        installArgs.put("agent_id", "echo-agent");
        callTool("install_agent", installArgs);

        ObjectNode configArgs = MAPPER.createObjectNode();
        configArgs.put("agent_id", "echo-agent");
        configArgs.put("auth_status", "REQUIRED");

        Map<String, Object> result = callTool("configure_agent", configArgs);

        assertTrue((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("Cannot set auth_status to REQUIRED"));
    }

    // -------------------------------------------------------------------
    // list_installed_agents
    // -------------------------------------------------------------------

    @Test
    void listInstalledAgentsReturnsAllInstalled() {
        // Install two agents
        installAgent("echo-agent");
        installAgent("cli-auth-agent");

        Map<String, Object> result = callTool("list_installed_agents", MAPPER.createObjectNode());

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("echo-agent"));
        assertTrue(text.contains("cli-auth-agent"));
        assertTrue(text.contains("totalCount"), "Should have totalCount: " + text);
        assertTrue(text.contains("TERMINAL_AUTH"));
    }

    // -------------------------------------------------------------------
    // get_agent_status
    // -------------------------------------------------------------------

    @Test
    void getAgentStatusReturnsDetails() {
        installAgent("cli-auth-agent");

        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "cli-auth-agent");

        Map<String, Object> result = callTool("get_agent_status", args);

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("cli-auth-agent"));
        assertTrue(text.contains("TERMINAL_AUTH"));
        assertTrue(text.contains("my-cli login"));
        assertTrue(text.contains("REQUIRED"));
    }

    @Test
    void getAgentStatusNotFoundReturnsError() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "nonexistent");

        Map<String, Object> result = callTool("get_agent_status", args);

        assertTrue((boolean) result.get("isError"));
    }

    @Test
    void getAgentStatusAllReturnsAllAgents() {
        installAgent("echo-agent");
        installAgent("cli-auth-agent");

        Map<String, Object> result = callTool("get_agent_status", MAPPER.createObjectNode());

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("echo-agent"));
        assertTrue(text.contains("cli-auth-agent"));
    }

    // -------------------------------------------------------------------
    // uninstall_agent
    // -------------------------------------------------------------------

    @Test
    void uninstallAgentSucceeds() {
        installAgent("echo-agent");

        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "echo-agent");

        Map<String, Object> result = callTool("uninstall_agent", args);

        assertFalse((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("UNINSTALLED"));

        assertTrue(store.findById("echo-agent").isEmpty());
    }

    @Test
    void uninstallNonExistentAgentReturnsError() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", "nonexistent");

        Map<String, Object> result = callTool("uninstall_agent", args);

        assertTrue((boolean) result.get("isError"));
        String text = extractText(result);
        assertTrue(text.contains("NOT_INSTALLED"));
    }

    // -------------------------------------------------------------------
    // Security: no secret leakage
    // -------------------------------------------------------------------

    @Test
    void noSecretLeakageInOutput() {
        installAgent("echo-agent");

        // Check all management tools
        String[] tools = {
                "list_agents",
                "refresh_registry",
                "list_installed_agents",
                "get_agent_status"
        };
        for (String tool : tools) {
            Map<String, Object> result = callTool(tool, MAPPER.createObjectNode());
            String text = extractText(result);

            // Auth setup commands are user-facing metadata and may contain
            // CLI flag names like "--token". We check for actual credential
            // patterns like full Bearer tokens or passwords.
            assertFalse(text.toLowerCase().contains("password"), tool + " leaked 'password'");
            assertFalse(text.toLowerCase().contains("secret_key"), tool + " leaked 'secret_key'");
            assertFalse(text.toLowerCase().contains("client_secret"), tool + " leaked 'client_secret'");
            assertFalse(text.contains("Bearer eyJ"), tool + " leaked JWT Bearer token");
        }
    }

    @Test
    void unknownToolReturnsError() {
        Map<String, Object> result = callTool("acp_registry/nonexistent_tool", MAPPER.createObjectNode());
        assertTrue((boolean) result.get("isError"));
        assertTrue(extractText(result).contains("Unknown tool"));
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    private void installAgent(String agentId) {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("agent_id", agentId);
        callTool("install_agent", args);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callTool(String toolName, com.fasterxml.jackson.databind.JsonNode arguments) {
        var future = provider.callTool(toolName, arguments);
        try {
            return future.toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> await(io.vertx.core.Future<List<Map<String, Object>>> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> toolResult) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) toolResult.get("content");
        return (String) content.get(0).get("text");
    }

    // -------------------------------------------------------------------
    // Test infrastructure — isolated from production classes
    // -------------------------------------------------------------------

    private static final class InMemoryAgentStoreOperations implements AgentStoreOperations {
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
        public synchronized Optional<InstalledAgent> findById(String agentId) {
            return Optional.ofNullable(agents.get(agentId));
        }

        @Override
        public synchronized InstalledAgent save(InstalledAgent agent) {
            agents.put(agent.agentId(), agent);
            return agent;
        }

        @Override
        public synchronized Optional<InstalledAgent> remove(String agentId) {
            return Optional.ofNullable(agents.remove(agentId));
        }

        @Override
        public Optional<InstalledAgent> setEnabled(String agentId, boolean enabled) {
            InstalledAgent existing = agents.get(agentId);
            if (existing == null) return Optional.empty();
            InstalledAgent updated = existing.withEnabled(enabled);
            agents.put(agentId, updated);
            return Optional.of(updated);
        }
    }

    private static final class NoOpAgentInstaller implements AgentInstaller {
        @Override
        public void materialize(InstallPlan plan) {
            // no-op
        }

        @Override
        public void remove(InstallPlan plan) {
            // no-op
        }
    }
}