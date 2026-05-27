package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.acp.AcpProviderConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstalledAgentAcpProviderFactoryTest {

    private static final String PYLOROS_MCP_URL = "http://127.0.0.1:8080/pyloros";
    private static final String PYLOROS_MCP_TOKEN = "shared-secret-token";

    @Test
    void enabledAgentProducesValidProviderConfiguration() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, configs.size());
        AcpProviderConfiguration config = configs.get(0);
        assertEquals("test-agent", config.id());
        assertEquals("test-agent/", config.prefix());
        assertEquals("agent", config.agentToolView());
    }

    @Test
    void disabledAgentIsNotRegistered() {
        InstalledAgent agent = createAgent("disabled-agent", "1.0.0", false, "disabled-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configs.isEmpty());
    }

    @Test
    void prefixDefaultsToAgentIdSlash() {
        InstalledAgent agent = createAgent("my-cool-agent", "2.0.0", true, "my-cool-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, null);

        assertEquals(1, configs.size());
        assertEquals("my-cool-agent/", configs.get(0).prefix());
    }

    @Test
    void agentToolViewIsNotPublic() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, null);

        assertEquals(1, configs.size());
        assertNotEquals("public", configs.get(0).agentToolView());
    }

    @Test
    void agentWithPublicViewIsRejected() {
        InstalledAgent agent = createAgent("bad-agent", "1.0.0", true, "bad-agent/", "public");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configs.isEmpty(), "agent with public agentToolView must be rejected");
    }

    @Test
    void mcpUrlInjectedIntoProcessEnvironment() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, configs.size());
        AcpProviderConfiguration config = configs.get(0);
        assertEquals(PYLOROS_MCP_URL, config.process().environment().get("PYLOROS_MCP_URL"));
        assertEquals(PYLOROS_MCP_TOKEN, config.process().environment().get("PYLOROS_MCP_BEARER_TOKEN"));
    }

    @Test
    void mcpTokenOmittedWhenBlank() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, "");

        assertEquals(1, configs.size());
        assertFalse(configs.get(0).process().environment().containsKey("PYLOROS_MCP_BEARER_TOKEN"));
    }

    @Test
    void mcpTokenOmittedWhenNull() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, null);

        assertEquals(1, configs.size());
        assertFalse(configs.get(0).process().environment().containsKey("PYLOROS_MCP_BEARER_TOKEN"));
    }

    @Test
    void recursiveAgentViewIsRejected() {
        // Agent whose agentToolView is "public" — triggers public-view recursion protection
        InstalledAgent agent = createAgent("recursive-agent", "1.0.0", true, "recursive-agent/", "public");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configs.isEmpty(), "agent with public agentToolView should be rejected");
    }

    @Test
    void selfReferencingAgentToolViewIsRejected() {
        // Agent where agentToolView equals agent ID
        InstalledAgent agent = createAgent("my-agent", "1.0.0", true, "my-agent/", "my-agent");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configs.isEmpty(), "agent whose agentToolView equals its own ID must be rejected");
    }

    @Test
    void multipleValidAgentsAllRegistered() {
        InstalledAgent agentA = createAgent("agent-a", "1.0.0", true, "agent-a/", "view-a");
        InstalledAgent agentB = createAgent("agent-b", "2.0.0", true, "agent-b/", "view-b");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agentA, agentB), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(2, configs.size());
        assertTrue(configs.stream().anyMatch(c -> "agent-a".equals(c.id())));
        assertTrue(configs.stream().anyMatch(c -> "agent-b".equals(c.id())));
    }

    @Test
    void mixedEnabledAndDisabledAgents() {
        InstalledAgent enabled = createAgent("enabled", "1.0.0", true, "enabled/", "view-e");
        InstalledAgent disabled = createAgent("disabled", "1.0.0", false, "disabled/", "view-d");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(enabled, disabled), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, configs.size());
        assertEquals("enabled", configs.get(0).id());
    }

    @Test
    void nullPylorosMcpUrlThrowsException() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        assertThrows(IllegalArgumentException.class, () ->
                InstalledAgentAcpProviderFactory.createConfigurations(List.of(agent), null, null));
    }

    @Test
    void blankPylorosMcpUrlThrowsException() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "agent");

        assertThrows(IllegalArgumentException.class, () ->
                InstalledAgentAcpProviderFactory.createConfigurations(List.of(agent), "   ", null));
    }

    @Test
    void emptyAgentListReturnsEmptyConfigurations() {
        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertTrue(configs.isEmpty());
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

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, PYLOROS_MCP_TOKEN);

        assertEquals(1, configs.size());
        AcpProviderConfiguration config = configs.get(0);
        assertEquals("npx", config.process().command());
        assertEquals(List.of("--yes", "@acme/npx-agent"), config.process().args());
    }

    @Test
    void exposeInViewsIsPublic() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true, "test-agent/", "my-view");

        List<AcpProviderConfiguration> configs = InstalledAgentAcpProviderFactory.createConfigurations(
                List.of(agent), PYLOROS_MCP_URL, null);

        assertEquals(1, configs.size());
        assertEquals(List.of("public"), configs.get(0).exposeInViews());
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
