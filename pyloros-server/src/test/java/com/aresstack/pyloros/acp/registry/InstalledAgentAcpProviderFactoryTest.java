package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.acp.AcpProviderConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InstalledAgentAcpProviderFactoryTest {

    private static final String PYLOROS_MCP_URL = "http://127.0.0.1:8080/pyloros";
    private static final String PYLOROS_MCP_TOKEN = "shared-secret-token";

    @Test
    void enabledAgentProducesValidProviderConfiguration() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size());
        assertTrue(result.failures().isEmpty());
        AcpProviderConfiguration config = result.configurations().get(0);
        assertEquals("test-agent", config.id());
        assertEquals("test-agent/", config.prefix());
        assertEquals("agent", config.agentToolView());
    }

    @Test
    void disabledAgentIsNotRegistered() {
        InstalledAgent agent = createAgent("disabled-agent", "1.0.0", false, "disabled-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertTrue(result.failures().isEmpty(), "disabled agents are not failures, just filtered");
    }

    @Test
    void prefixDefaultsToAgentIdSlash() {
        InstalledAgent agent = createAgent("my-cool-agent", "2.0.0", true, "my-cool-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, null);

        assertEquals(1, result.configurations().size());
        assertEquals("my-cool-agent/", result.configurations().get(0).prefix());
    }

    @Test
    void rootPrefixIsAllowedAsExplicitOptIn() {
        InstalledAgent agent = createAgent("root-agent", "1.0.0", true, "/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, null);

        assertEquals(1, result.configurations().size());
        assertEquals("/", result.configurations().get(0).prefix());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void prefixWithoutTrailingSlashIsRejectedAsFailure() {
        InstalledAgent agent = createAgent("bad-prefix", "1.0.0", true, "no-slash", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals("bad-prefix", result.failures().get(0).agentId());
        assertTrue(result.failures().get(0).reason().contains("must end with '/'"));
    }

    @Test
    void agentToolViewIsNotPublic() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, null);

        assertEquals(1, result.configurations().size());
        assertNotEquals("public", result.configurations().get(0).agentToolView());
    }

    @Test
    void agentWithPublicViewIsRejectedAsFailure() {
        InstalledAgent agent = createAgent("bad-agent", "1.0.0", true, "bad-agent/", "public");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals("bad-agent", result.failures().get(0).agentId());
        assertTrue(result.failures().get(0).reason().contains("public"));
    }

    @Test
    void mcpUrlInjectedIntoProcessEnvironment() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size());
        AcpProviderConfiguration config = result.configurations().get(0);
        assertEquals(PYLOROS_MCP_URL, config.process().environment().get("PYLOROS_MCP_URL"));
        assertEquals(PYLOROS_MCP_TOKEN, config.process().environment().get("PYLOROS_MCP_BEARER_TOKEN"));
    }

    @Test
    void mcpTokenOmittedWhenBlank() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, "");

        assertEquals(1, result.configurations().size());
        assertFalse(result.configurations().get(0).process().environment().containsKey("PYLOROS_MCP_BEARER_TOKEN"));
    }

    @Test
    void mcpTokenOmittedWhenNull() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, null);

        assertEquals(1, result.configurations().size());
        assertFalse(result.configurations().get(0).process().environment().containsKey("PYLOROS_MCP_BEARER_TOKEN"));
    }

    @Test
    void selfReferencingAgentToolViewIsRejectedAsFailure() {
        InstalledAgent agent = createAgent("my-agent", "1.0.0", true, "my-agent/", "my-agent");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals("my-agent", result.failures().get(0).agentId());
    }

    @Test
    void agentViewCollidingWithExistingNonRegistryProviderIsRejected() {
        // Existing non-registry ACP provider "github" is exposed in view "shared-view"
        InstalledAgent agent = createAgent("registry-agent", "1.0.0", true, "registry-agent/", "shared-view");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent),
                Set.of("github"),
                Map.of("shared-view", Set.of("github")),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals("registry-agent", result.failures().get(0).agentId());
        assertTrue(result.failures().get(0).reason().contains("github"));
    }

    @Test
    void agentViewReferencingExistingNonRegistryProviderIdIsRejected() {
        // Agent uses agentToolView that equals an existing non-registry provider ID
        InstalledAgent agent = createAgent("registry-agent", "1.0.0", true, "registry-agent/", "github");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent),
                Set.of("github"),
                Map.of(),
                PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertEquals(1, result.failures().size());
        assertEquals("registry-agent", result.failures().get(0).agentId());
    }

    @Test
    void multipleValidAgentsAllRegistered() {
        InstalledAgent agentA = createAgent("agent-a", "1.0.0", true, "agent-a/", "view-a");
        InstalledAgent agentB = createAgent("agent-b", "2.0.0", true, "agent-b/", "view-b");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agentA, agentB), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(2, result.configurations().size());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.configurations().stream().anyMatch(c -> "agent-a".equals(c.id())));
        assertTrue(result.configurations().stream().anyMatch(c -> "agent-b".equals(c.id())));
    }

    @Test
    void mixedEnabledAndDisabledAgents() {
        InstalledAgent enabled = createAgent("enabled", "1.0.0", true, "enabled/", "view-e");
        InstalledAgent disabled = createAgent("disabled", "1.0.0", false, "disabled/", "view-d");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(enabled, disabled), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size());
        assertEquals("enabled", result.configurations().get(0).id());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void nullPylorosMcpUrlThrowsException() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        assertThrows(IllegalArgumentException.class, () ->
                InstalledAgentAcpProviderFactory.createConfigurations(
                        List.of(agent), Set.of(), Map.of(), null, null));
    }

    @Test
    void blankPylorosMcpUrlThrowsException() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        assertThrows(IllegalArgumentException.class, () ->
                InstalledAgentAcpProviderFactory.createConfigurations(
                        List.of(agent), Set.of(), Map.of(), "   ", null));
    }

    @Test
    void emptyAgentListReturnsEmptyResult() {
        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(result.configurations().isEmpty());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void processConfigurationUsesResolvedCommandAndArgs() {
        InstalledAgent agent = new InstalledAgent(
                "npx-agent", "3.0.0", "npx", "npx",
                List.of("--yes", "@acme/npx-agent"),
                "/install/npx-agent", "v1", "MIT",
                "npx-agent/", "agent-view", true,
                Instant.now(), Instant.now()
        );

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size());
        AcpProviderConfiguration config = result.configurations().get(0);
        assertEquals("npx", config.process().command());
        assertEquals(List.of("--yes", "@acme/npx-agent"), config.process().args());
    }

    @Test
    void exposeInViewsIsPublic() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "my-view");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), Set.of(), Map.of(), PYLOROS_MCP_URL, null);

        assertEquals(1, result.configurations().size());
        assertEquals(List.of("public"), result.configurations().get(0).exposeInViews());
    }

    @Test
    void mixedValidAndInvalidAgentsReturnsBothConfigsAndFailures() {
        InstalledAgent valid = createAgent("valid-agent", "1.0.0", true, "valid-agent/", "valid-view");
        InstalledAgent invalid = createAgent("invalid-agent", "1.0.0", true, "invalid-agent/", "public");

        var result = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(valid, invalid), Set.of(), Map.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, result.configurations().size());
        assertEquals("valid-agent", result.configurations().get(0).id());
        assertEquals(1, result.failures().size());
        assertEquals("invalid-agent", result.failures().get(0).agentId());
    }

    private static InstalledAgent createAgent(String agentId, String version, boolean enabled,
                                              String prefix, String agentToolView) {
        Instant now = Instant.now();
        return new InstalledAgent(
                agentId, version, "npx", "npx",
                List.of("--yes", "@acme/" + agentId),
                "/install/" + agentId, "v1", "MIT",
                prefix, agentToolView, enabled, now, now
        );
    }
}
