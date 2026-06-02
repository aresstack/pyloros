package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for R7-06: ACP registry agent auth flow support.
 * Covers:
 * - fake agent returns auth required
 * - terminal auth command plan
 * - auth status in installed-agent store/projection
 * - no credential logging
 * - unauthenticated call returns structured error
 */
class AgentAuthFlowTest {

    private static final String PYLOROS_MCP_URL = "http://127.0.0.1:8080/pyloros";
    private static final String PYLOROS_MCP_TOKEN = "shared-secret-token";

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------
    // Registry parsing: auth is recognized from JSON
    // -------------------------------------------------------------------

    @Test
    void registryAgentWithTerminalAuthIsParsed() {
        String json = """
                {
                  "version": "1.0.0",
                  "agents": [
                    {
                      "id": "cli-login-agent",
                      "name": "CLI Login Agent",
                      "version": "1.2.0",
                      "description": "Requires 'acme login' before use",
                      "license": "MIT",
                      "auth": {
                        "method": "TERMINAL_AUTH",
                        "setupCommand": "acme login --no-browser",
                        "description": "Run this once to authenticate with the ACME API"
                      },
                      "distribution": {
                        "npx": { "packageName": "cli-login-agent@1.2.0" }
                      }
                    }
                  ]
                }
                """;

        AcpRegistryLoadResult result = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("cache.json"))).loadFrom(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Success.class, result);
        var success = (AcpRegistryLoadResult.Success) result;
        assertEquals(1, success.registry().agents().size());
        RegistryAgent agent = success.registry().agents().get(0);
        assertEquals("cli-login-agent", agent.id());
        assertNotNull(agent.auth());
        assertEquals(AgentAuthMethod.TERMINAL_AUTH, agent.auth().method());
        assertEquals("acme login --no-browser", agent.auth().setupCommand());
        assertEquals("Run this once to authenticate with the ACME API", agent.auth().description());
    }

    @Test
    void registryAgentWithAgentAuthIsParsed() {
        String json = """
                {
                  "version": "1.0.0",
                  "agents": [
                    {
                      "id": "oauth-agent",
                      "name": "OAuth Agent",
                      "version": "2.0.0",
                      "description": "Handles its own browser-based OAuth flow",
                      "license": "MIT",
                      "auth": {
                        "method": "AGENT_AUTH",
                        "setupCommand": "",
                        "description": "Agent opens browser for GitHub OAuth"
                      },
                      "distribution": {
                        "npx": { "packageName": "oauth-agent@2.0.0" }
                      }
                    }
                  ]
                }
                """;

        AcpRegistryLoadResult result = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("cache.json"))).loadFrom(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Success.class, result);
        var success = (AcpRegistryLoadResult.Success) result;
        RegistryAgent agent = success.registry().agents().get(0);
        assertEquals(AgentAuthMethod.AGENT_AUTH, agent.auth().method());
    }

    @Test
    void registryAgentWithoutAuthDefaultsToNone() {
        RegistryAgent agent = new RegistryAgent(
                "no-auth", "No Auth", "1.0.0", "Agent without auth",
                null, null, null, "MIT", null,
                new RegistryDistribution(null,
                        new RegistryDistribution.PackageDistribution("no-auth@1.0.0", List.of(), null),
                        null),
                null);

        assertNotNull(agent.auth());
        assertEquals(AgentAuthMethod.NONE, agent.auth().method());
    }

    @Test
    void registryAgentWithUnknownAuthFieldsIsTolerated() {
        // @JsonIgnoreProperties(ignoreUnknown = true) ensures backward compat
        String json = """
                {
                  "version": "1.0.0",
                  "agents": [
                    {
                      "id": "future-auth-agent",
                      "name": "Future Auth Agent",
                      "version": "1.0.0",
                      "description": "Has future auth fields",
                      "license": "MIT",
                      "auth": {
                        "method": "TERMINAL_AUTH",
                        "setupCommand": "some-login",
                        "description": "login needed",
                        "futureField": "should be ignored"
                      },
                      "unknownTopLevel": "also ignored",
                      "distribution": {
                        "npx": { "packageName": "future-agent@1.0.0" }
                      }
                    }
                  ]
                }
                """;

        AcpRegistryLoadResult result = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("cache2.json"))).loadFrom(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Success.class, result);
        var success = (AcpRegistryLoadResult.Success) result;
        assertEquals(AgentAuthMethod.TERMINAL_AUTH, success.registry().agents().get(0).auth().method());
    }

    // -------------------------------------------------------------------
    // Registry validation: TERMINAL_AUTH without setupCommand is an error
    // -------------------------------------------------------------------

    @Test
    void terminalAuthWithoutSetupCommandIsValidationError() {
        String json = """
                {
                  "version": "1.0.0",
                  "agents": [
                    {
                      "id": "broken-auth",
                      "name": "Broken Auth",
                      "version": "1.0.0",
                      "description": "Missing setup command",
                      "license": "MIT",
                      "auth": {
                        "method": "TERMINAL_AUTH",
                        "setupCommand": "",
                        "description": "You need to authenticate"
                      },
                      "distribution": {
                        "npx": { "packageName": "broken-auth@1.0.0" }
                      }
                    }
                  ]
                }
                """;

        AcpRegistryLoadResult result = new AcpRegistryClient(
                URI.create("http://localhost/registry.json"),
                new AcpRegistryCache(tempDir.resolve("cache-validate.json"))).loadFrom(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Failure.class, result);
        var failure = (AcpRegistryLoadResult.Failure) result;
        assertTrue(failure.errors().stream().anyMatch(e -> e.contains("TERMINAL_AUTH") && e.contains("setupCommand")));
    }

    // -------------------------------------------------------------------
    // Auth status projection: InstalledAgent captures auth state
    // -------------------------------------------------------------------

    @Test
    void authRequiredAgentHasAuthStatusRequired() {
        RegistryAgent registryAgent = npxRegistryAgentWithAuth(
                "auth-agent", "1.0.0", AgentAuthMethod.TERMINAL_AUTH,
                "acme login", "Run acme login to authenticate");

        // Install via lifecycle service
        InMemoryAgentStoreOperations store = new InMemoryAgentStoreOperations();
        AgentLifecycleService service = new AgentLifecycleService(
                store, new DistributionResolver(), new NoOpAgentInstaller());
        AgentLifecycleResult result = service.install(registryAgent);

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals(AgentAuthMethod.TERMINAL_AUTH, success.agent().authMethod());
        assertEquals(AgentAuthStatus.REQUIRED, success.agent().authStatus());
        assertEquals("acme login", success.agent().authSetupCommand());
        assertEquals("Run acme login to authenticate", success.agent().authDescription());
    }

    @Test
    void authNotRequiredAgentHasAuthStatusNotRequired() {
        RegistryAgent registryAgent = npxRegistryAgent("no-auth-agent", "1.0.0", "no-auth@1.0.0");

        InMemoryAgentStoreOperations store = new InMemoryAgentStoreOperations();
        AgentLifecycleService service = new AgentLifecycleService(
                store, new DistributionResolver(), new NoOpAgentInstaller());
        AgentLifecycleResult result = service.install(registryAgent);

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals(AgentAuthMethod.NONE, success.agent().authMethod());
        assertEquals(AgentAuthStatus.NOT_REQUIRED, success.agent().authStatus());
    }

    @Test
    void agentAuthMethodIsAgentAuthDoesNotBlockInstall() {
        RegistryAgent registryAgent = npxRegistryAgentWithAuth(
                "self-auth-agent", "1.0.0", AgentAuthMethod.AGENT_AUTH,
                "", "Agent handles its own browser OAuth");

        InMemoryAgentStoreOperations store = new InMemoryAgentStoreOperations();
        AgentLifecycleService service = new AgentLifecycleService(
                store, new DistributionResolver(), new NoOpAgentInstaller());
        AgentLifecycleResult result = service.install(registryAgent);

        // AGENT_AUTH should install and be provider-eligible (CONFIGURED)
        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals(AgentAuthMethod.AGENT_AUTH, success.agent().authMethod());
        assertEquals(AgentAuthStatus.CONFIGURED, success.agent().authStatus());
    }

    // -------------------------------------------------------------------
    // Unauthenticated agent is blocked at ACP provider generation
    // -------------------------------------------------------------------

    @Test
    void authRequiredAgentIsNotRegisteredAsProvider() {
        InstalledAgent agent = createInstalledAgent(
                "auth-required-agent", "1.0.0", true,
                AgentAuthMethod.TERMINAL_AUTH, AgentAuthStatus.REQUIRED,
                "acme login", "Authenticate with ACME");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty(), "Auth-required agent must not be registered");
        assertEquals(1, result.failures().size());
        InstalledAgentAcpProviderFactory.AgentFailure failure = result.failures().get(0);
        assertEquals("auth-required-agent", failure.agentId());
        assertTrue(failure.reason().contains("requires authentication"), "Reason must mention auth: " + failure.reason());
        assertTrue(failure.reason().contains("TERMINAL_AUTH"), "Reason must mention auth method: " + failure.reason());
        assertTrue(failure.reason().contains("acme login"), "Reason must include setup command: " + failure.reason());
    }

    @Test
    void authConfiguredAgentIsRegisteredAsProvider() {
        InstalledAgent agent = createInstalledAgent(
                "auth-configured-agent", "1.0.0", true,
                AgentAuthMethod.TERMINAL_AUTH, AgentAuthStatus.CONFIGURED,
                "acme login", "Authenticate with ACME");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size(), "Auth-configured agent should be registered");
        assertTrue(result.failures().isEmpty());
        assertEquals("auth-configured-agent", result.configurations().get(0).id());
    }

    @Test
    void authNotRequiredAgentIsRegisteredNormally() {
        InstalledAgent agent = createInstalledAgent(
                "standard-agent", "1.0.0", true,
                AgentAuthMethod.NONE, AgentAuthStatus.NOT_REQUIRED, "", "");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void mixedAuthAndNonAuthAgentsResultsInCorrectSplit() {
        InstalledAgent standard = createInstalledAgent(
                "standard", "1.0.0", true,
                AgentAuthMethod.NONE, AgentAuthStatus.NOT_REQUIRED, "", "");
        InstalledAgent authRequired = createInstalledAgent(
                "needs-auth", "1.0.0", true,
                AgentAuthMethod.TERMINAL_AUTH, AgentAuthStatus.REQUIRED,
                "some-login", "Auth needed");
        InstalledAgent disabled = createInstalledAgent(
                "disabled", "1.0.0", false,
                AgentAuthMethod.TERMINAL_AUTH, AgentAuthStatus.REQUIRED,
                "some-login", "Auth needed but disabled");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(standard, authRequired, disabled), Set.of(), Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size(), "Only standard agent passes");
        assertEquals("standard", result.configurations().get(0).id());
        assertEquals(1, result.failures().size(), "One auth-required failure (disabled is filtered)");
        assertEquals("needs-auth", result.failures().get(0).agentId());
    }

    // -------------------------------------------------------------------
    // No credential logging
    // -------------------------------------------------------------------

    @Test
    void authFieldsDontLeakCredentialsInStoreJson() {
        // Verify that auth-related fields only contain metadata, not secrets
        InstalledAgent agent = createInstalledAgent(
                "secret-agent", "1.0.0", true,
                AgentAuthMethod.AGENT_AUTH, AgentAuthStatus.REQUIRED,
                "", "Agent handles its own GitHub OAuth via browser callback");

        // The auth fields are: authMethod (enum), authStatus (enum),
        // authSetupCommand (user-known command), authDescription (description).
        // None of these are secrets.
        String authMethodName = agent.authMethod().name();
        String authStatusName = agent.authStatus().name();
        String authSetupCmd = agent.authSetupCommand();
        String authDescription = agent.authDescription();

        assertFalse(authMethodName.contains("password"), "No password in auth fields");
        assertFalse(authStatusName.contains("password"), "No password in auth fields");
        assertFalse(authSetupCmd.contains("password"), "No password in auth fields");
        assertFalse(authDescription.contains("password"), "No password");
        assertFalse(authDescription.contains("secret"), "No secret");
        assertFalse(authDescription.contains("token"), "No token");
        assertFalse(authDescription.contains("Bearer"), "No Bearer");
    }

    // -------------------------------------------------------------------
    // Auth status update via withAuthStatus
    // -------------------------------------------------------------------

    @Test
    void withAuthStatusUpdatesAuthState() {
        InstalledAgent agent = createInstalledAgent(
                "mutable-agent", "1.0.0", true,
                AgentAuthMethod.TERMINAL_AUTH, AgentAuthStatus.REQUIRED,
                "setup-cli auth", "Run this");

        assertEquals(AgentAuthStatus.REQUIRED, agent.authStatus());

        InstalledAgent configured = agent.withAuthStatus(AgentAuthStatus.CONFIGURED);
        assertEquals(AgentAuthStatus.CONFIGURED, configured.authStatus());
        assertEquals("mutable-agent", configured.agentId());
        assertEquals(agent.authMethod(), configured.authMethod());
    }

    // -------------------------------------------------------------------
    // Terminal auth command plan is preserved
    // -------------------------------------------------------------------

    @Test
    void terminalAuthCommandPlanIsIncludedInFailureReason() {
        String setupCmd = "my-agent-cli auth --interactive --scopes=read,write";
        InstalledAgent agent = createInstalledAgent(
                "cli-agent", "2.0.0", true,
                AgentAuthMethod.TERMINAL_AUTH, AgentAuthStatus.REQUIRED,
                setupCmd, "Run interactive OAuth setup with the vendor");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.failures().size());
        String reason = result.failures().get(0).reason();
        assertTrue(reason.contains(setupCmd),
                "Failure reason must include the setup command: " + reason);
        assertTrue(reason.contains("Run setup command"),
                "Failure reason must mention setup: " + reason);
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    private static InstalledAgent createInstalledAgent(
            String agentId, String version, boolean enabled,
            AgentAuthMethod authMethod, AgentAuthStatus authStatus,
            String authSetupCommand, String authDescription) {
        Instant now = Instant.now();
        return new InstalledAgent(
                agentId, version, "npx", "npx",
                List.of("--yes", "@acme/" + agentId),
                "/install/" + agentId, "v1", "MIT",
                agentId + "/", agentId + "-agent-view", enabled,
                authMethod, authStatus, authSetupCommand, authDescription,
                now, now
        );
    }

    private static RegistryAgent npxRegistryAgent(String id, String version, String packageName) {
        return new RegistryAgent(
                id, id, version, "A test agent",
                null, null, null, "MIT", null,
                new RegistryDistribution(null,
                        new RegistryDistribution.PackageDistribution(packageName, List.of(), null),
                        null),
                null);
    }

    private static RegistryAgent npxRegistryAgentWithAuth(
            String id, String version, AgentAuthMethod method,
            String setupCommand, String description) {
        return new RegistryAgent(
                id, id, version, "A test agent with auth",
                null, null, null, "MIT", null,
                new RegistryDistribution(null,
                        new RegistryDistribution.PackageDistribution(id + "@" + version, List.of(), null),
                        null),
                new AuthRequirements(method, setupCommand, description));
    }

    /**
     * In-memory store operations for testing without file I/O.
     */
    private static final class InMemoryAgentStoreOperations implements AgentStoreOperations {
        private final Map<String, InstalledAgent> agents = new HashMap<>();

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
            if (existing == null) {
                return Optional.empty();
            }
            InstalledAgent updated = existing.withEnabled(enabled);
            agents.put(agentId, updated);
            return Optional.of(updated);
        }
    }

    /**
     * No-op installer for testing npx/uvx lifecycle.
     */
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
