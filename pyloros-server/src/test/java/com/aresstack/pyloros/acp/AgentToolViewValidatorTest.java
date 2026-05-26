package com.aresstack.pyloros.acp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolViewValidatorTest {

    @Test
    void testAllowsManagerAgentToolViewWhenIsolated() {
        assertDoesNotThrow(() -> AgentToolViewValidator.validate(config("manager", "manager-agent-view"), Set.of("manager", "other-acp")));
    }

    @Test
    void testRejectsManagerSelfReference() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("manager", "manager"), Set.of("manager", "other-acp")));

        assertEquals(
                "Invalid agentToolView for ACP provider 'manager': 'manager' references the same provider ID "
                        + "and would allow self-recursion.",
                exception.getMessage());
    }

    @Test
    void testRejectsManagerReferenceToOtherAcpProvider() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("manager", "other-acp"), Set.of("manager", "other-acp")));

        assertEquals(
                "Invalid agentToolView for ACP provider 'manager': 'other-acp' references another ACP provider ID "
                        + "and may trigger recursive agent delegation.",
                exception.getMessage());
    }

    @Test
    void testRejectsPublicView() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("manager", "public"), Set.of("manager")));

        assertEquals(
                "Invalid agentToolView for ACP provider 'manager': 'public' must not be 'public' "
                        + "because it would expose the public tool view to the agent (recursion risk).",
                exception.getMessage());
    }

    @Test
    void testRejectsPublicViewCaseInsensitive() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("manager", "Public"), Set.of("manager")));

        assertEquals(
                "Invalid agentToolView for ACP provider 'manager': 'Public' must not be 'public' "
                        + "because it would expose the public tool view to the agent (recursion risk).",
                exception.getMessage());
    }

    @Test
    void testRejectsManagerAgentToolViewInExposeInViews() {
        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "manager",
                "manager/",
                "manager-agent-view",
                List.of("public", "manager-agent-view"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config, Set.of("manager")));

        assertEquals(
                "Invalid agentToolView for ACP provider 'manager': 'manager-agent-view' collides with exposeInViews "
                        + "[public, manager-agent-view] so the provider would see its own ACP tools "
                        + "(self-recursion risk).",
                exception.getMessage());
    }

    private static AcpProviderConfiguration config(String providerId, String agentToolView) {
        return new AcpProviderConfiguration(
                providerId,
                providerId + "/",
                agentToolView,
                List.of("public"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration());
    }
}
