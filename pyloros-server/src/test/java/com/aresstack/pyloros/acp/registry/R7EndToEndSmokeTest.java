package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke test for R7: Registry → Install → Store →
 * Managed ACP Provider → session/new with mcpServers.pyloros.
 *
 * <p>Verifies the full pipeline from fake registry to generated
 * ACP provider configuration without launching real processes.
 */
class R7EndToEndSmokeTest {

    private static final String PYLOROS_MCP_URL = "http://127.0.0.1:8081/pyloros";
    private static final String PYLOROS_MCP_TOKEN = "smoke-test-view-token";

    private static final String FAKE_REGISTRY_JSON = """
            {
              "version": "1.0.0",
              "agents": [
                {
                  "id": "smoke-agent",
                  "name": "Smoke Test Agent",
                  "version": "1.0.0",
                  "description": "An agent for R7 end-to-end smoke testing",
                  "repository": "https://github.com/aresstack/pyloros",
                  "license": "MIT",
                  "authors": ["AresStack"],
                  "distribution": {
                    "npx": { "package": "smoke-agent@1.0.0" }
                  }
                },
                {
                  "id": "auth-smoke-agent",
                  "name": "Auth Smoke Agent",
                  "version": "1.0.0",
                  "description": "Smoke agent that requires terminal auth",
                  "license": "Apache-2.0",
                  "auth": {
                    "method": "TERMINAL_AUTH",
                    "setupCommand": "smoke-agent auth login",
                    "description": "Authenticate before use"
                  },
                  "distribution": {
                    "npx": { "package": "auth-smoke-agent@1.0.0" }
                  }
                }
              ]
            }
            """;

    @TempDir
    Path tempDir;

    private AcpRegistryClient registryClient;
    private InMemoryAgentStoreOperations store;
    private AgentLifecycleService lifecycleService;
    private DistributionResolver distributionResolver;
    private NoOpAgentInstaller installer;

    @BeforeEach
    void setUp() {
        store = new InMemoryAgentStoreOperations();
        registryClient = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("r7-smoke-cache.json")));
        distributionResolver = new DistributionResolver();
        installer = new NoOpAgentInstaller();
        lifecycleService = new AgentLifecycleService(store, distributionResolver, installer);
    }

    // -------------------------------------------------------------------
    // E2E: Registry → Install → Store verification
    // -------------------------------------------------------------------

    @Test
    void fullPipelineRegistryToInstalledStore() {
        // Step 1: Load fake registry
        AcpRegistryLoadResult loadResult = registryClient.loadFrom(
                new ByteArrayInputStream(FAKE_REGISTRY_JSON.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Success.class, loadResult);
        var success = (AcpRegistryLoadResult.Success) loadResult;
        assertEquals(2, success.registry().agents().size());
        assertEquals("1.0.0", success.registry().version());

        RegistryAgent smokeAgent = success.registry().agents().get(0);
        assertEquals("smoke-agent", smokeAgent.id());
        assertEquals("Smoke Test Agent", smokeAgent.name());
        assertEquals("smoke-agent@1.0.0", success.registry().agents().get(0).distribution().npx().packageName());

        // Step 2: Install agent
        AgentLifecycleResult installResult = lifecycleService.install(smokeAgent);
        assertInstanceOf(AgentLifecycleResult.Success.class, installResult);
        var installSuccess = (AgentLifecycleResult.Success) installResult;
        assertEquals(AgentLifecycleResult.Action.INSTALLED, installSuccess.action());
        assertEquals("smoke-agent", installSuccess.agent().agentId());
        assertEquals("1.0.0", installSuccess.agent().installedVersion());

        // Step 3: Verify installed store
        InstalledAgent installed = store.findById("smoke-agent").orElseThrow();
        assertEquals("smoke-agent", installed.agentId());
        assertEquals("1.0.0", installed.installedVersion());
        assertEquals("npx", installed.distributionType());
        assertEquals("smoke-agent/", installed.configuredPrefix(), "Default prefix should be agentId/");
        assertEquals("smoke-agent-agent-view", installed.agentToolView(), "Default view should be agentId-agent-view");
        assertTrue(installed.enabled());
        assertEquals(AgentAuthMethod.NONE, installed.authMethod());
        assertEquals(AgentAuthStatus.NOT_REQUIRED, installed.authStatus());
    }

    // -------------------------------------------------------------------
    // E2E: Installed Store → Managed ACP Provider config
    // -------------------------------------------------------------------

    @Test
    void installedAgentGeneratesValidProviderConfiguration() {
        // Install agent
        loadRegistryAndInstall("smoke-agent");

        // Generate provider configs
        var configResult = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(), java.util.Set.of(), java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, configResult.configurations().size());
        assertTrue(configResult.failures().isEmpty());

        com.aresstack.pyloros.acp.AcpProviderConfiguration config = configResult.configurations().get(0);

        // Verify core config properties
        assertEquals("smoke-agent", config.id());
        assertEquals("smoke-agent/", config.prefix());
        assertEquals("smoke-agent-agent-view", config.agentToolView());
        assertNotEquals("public", config.agentToolView(), "agentToolView must not be public");

        // Verify exposed views
        assertEquals(List.of("public"), config.exposeInViews(),
                "Agent tools should be exposed in public view");

        // Verify process configuration
        assertLaunchCommand("npx", config.process().command());
        assertNotNull(config.process().args());

        // Verify mcpServers.pyloros injection via environment
        Map<String, String> env = config.process().environment();
        assertEquals(PYLOROS_MCP_URL, env.get("PYLOROS_MCP_URL"),
                "PYLOROS_MCP_URL must be injected for session/new");
        assertEquals(PYLOROS_MCP_TOKEN, env.get("PYLOROS_MCP_BEARER_TOKEN"),
                "PYLOROS_MCP_BEARER_TOKEN must be injected for session/new");
    }

    // -------------------------------------------------------------------
    // E2E: Auth agent blocked at provider generation
    // -------------------------------------------------------------------

    @Test
    void authRequiredAgentIsBlockedAtProviderGeneration() {
        // Install the auth agent
        loadRegistryAndInstall("auth-smoke-agent");

        // Auth agent should be installed but with REQUIRED status
        InstalledAgent authAgent = store.findById("auth-smoke-agent").orElseThrow();
        assertEquals(AgentAuthMethod.TERMINAL_AUTH, authAgent.authMethod());
        assertEquals(AgentAuthStatus.REQUIRED, authAgent.authStatus());

        // Generate provider configs - auth agent should be blocked
        var configResult = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(), java.util.Set.of(), java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configResult.configurations().isEmpty(),
                "Auth-required agent must not be registered");
        assertEquals(1, configResult.failures().size());
        InstalledAgentAcpProviderFactory.AgentFailure failure = configResult.failures().get(0);
        assertEquals("auth-smoke-agent", failure.agentId());
        assertTrue(failure.reason().contains("requires authentication"));

        // After user configures auth, agent should pass
        InstalledAgent configured = authAgent.withAuthStatus(AgentAuthStatus.CONFIGURED);
        store.save(configured);

        var configResult2 = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(), java.util.Set.of(), java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, configResult2.configurations().size());
        assertEquals("auth-smoke-agent", configResult2.configurations().get(0).id());
        assertTrue(configResult2.failures().isEmpty());
    }

    // -------------------------------------------------------------------
    // E2E: Non-public view requires explicit non-public view selection
    // -------------------------------------------------------------------

    @Test
    void nonPublicAgentToolViewEnforced() {
        loadRegistryAndInstall("smoke-agent");

        var configResult = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(), java.util.Set.of(), java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        var config = configResult.configurations().get(0);
        assertNotEquals("public", config.agentToolView());
        assertEquals(List.of("public"), config.exposeInViews(),
                "Agent tools exposed in public view, but agent itself sees non-public view");
    }

    // -------------------------------------------------------------------
    // E2E: Root prefix requires explicit opt-in
    // -------------------------------------------------------------------

    @Test
    void rootPrefixRequiresExplicitOptIn() {
        loadRegistryAndInstall("smoke-agent");

        InstalledAgent agent = store.findById("smoke-agent").orElseThrow();
        assertEquals("smoke-agent/", agent.configuredPrefix(),
                "Default prefix should NOT be root");

        // Root opt-in should be allowed explicitly
        var configResult = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(), java.util.Set.of(), java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);
        assertNotEquals("/", configResult.configurations().get(0).prefix(),
                "Default should not be root prefix");
    }

    // -------------------------------------------------------------------
    // E2E: Multiple agents with separate views
    // -------------------------------------------------------------------

    @Test
    void multipleAgentsHaveSeparateViews() {
        loadRegistryAndInstall("smoke-agent");

        // Also install auth-smoke-agent after configuring
        loadRegistryAndInstall("auth-smoke-agent");
        InstalledAgent authAgent = store.findById("auth-smoke-agent").orElseThrow();
        store.save(authAgent.withAuthStatus(AgentAuthStatus.CONFIGURED));

        var configResult = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(), java.util.Set.of(), java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(2, configResult.configurations().size());
        assertTrue(configResult.failures().isEmpty());

        var config1 = configResult.configurations().get(0);
        var config2 = configResult.configurations().get(1);
        assertNotEquals(config1.agentToolView(), config2.agentToolView(),
                "Each agent must have a unique agentToolView");
    }

    // -------------------------------------------------------------------
    // E2E: Recursion protection
    // -------------------------------------------------------------------

    @Test
    void recursionProtectionBlocksSelfReferencingView() {
        // Install an agent and configure it with a view that equals another provider ID
        loadRegistryAndInstall("smoke-agent");
        InstalledAgent agent = store.findById("smoke-agent").orElseThrow();
        // Manually set agentToolView to match an existing non-registry provider ID
        InstalledAgent conflictAgent = new InstalledAgent(
                agent.agentId(), agent.installedVersion(), agent.distributionType(),
                agent.resolvedCommand(), agent.resolvedArgs(), agent.installPath(),
                agent.sourceRegistryVersion(), agent.license(),
                agent.configuredPrefix(), "github", agent.enabled(),
                agent.authMethod(), agent.authStatus(), agent.authSetupCommand(), agent.authDescription(),
                agent.installedAt(), Instant.now());
        store.save(conflictAgent);

        var configResult = InstalledAgentAcpProviderFactory.createConfigurations(
                store.listAll(),
                java.util.Set.of("github"),   // existing non-registry provider
                java.util.Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configResult.configurations().isEmpty());
        assertEquals(1, configResult.failures().size());
        assertTrue(configResult.failures().get(0).reason().contains("github"));
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static void assertLaunchCommand(String expectedBaseCommand, String actualCommand) {
        assertNotNull(actualCommand);
        if (TargetPlatform.current().os().equals("windows")) {
            assertTrue(actualCommand.endsWith(expectedBaseCommand + ".cmd"), actualCommand);
        } else {
            assertEquals(expectedBaseCommand, actualCommand);
        }
    }

    private void loadRegistryAndInstall(String agentId) {
        AcpRegistryLoadResult loadResult = registryClient.loadFrom(
                new ByteArrayInputStream(FAKE_REGISTRY_JSON.getBytes(StandardCharsets.UTF_8)));
        var success = (AcpRegistryLoadResult.Success) loadResult;
        RegistryAgent agent = success.registry().agents().stream()
                .filter(a -> a.id().equals(agentId))
                .findFirst()
                .orElseThrow();
        AgentLifecycleResult installResult = lifecycleService.install(agent);
        assertInstanceOf(AgentLifecycleResult.Success.class, installResult,
                "Install of " + agentId + " should succeed");
    }

    // -------------------------------------------------------------------
    // Test infrastructure
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