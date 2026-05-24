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
    void testAllowsNonAcpAgentToolView() {
        assertDoesNotThrow(() -> AgentToolViewValidator.validate(config("copilot", "agent"), Set.of("copilot", "other-acp")));
    }

    @Test
    void testRejectsSelfReference() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("copilot", "copilot"), Set.of("copilot", "other-acp")));

        assertEquals("agentToolView must not reference ACP provider itself: copilot", exception.getMessage());
    }

    @Test
    void testRejectsOtherAcpProviderReference() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("copilot", "other-acp"), Set.of("copilot", "other-acp")));

        assertEquals("agentToolView must not reference another ACP provider: other-acp", exception.getMessage());
    }

    @Test
    void testRejectsPublicView() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("copilot", "public"), Set.of("copilot")));

        assertEquals("agentToolView must not be 'public' — ACP agents must not see the public tool view: public", exception.getMessage());
    }

    @Test
    void testRejectsPublicViewCaseInsensitive() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config("copilot", "Public"), Set.of("copilot")));

        assertEquals("agentToolView must not be 'public' — ACP agents must not see the public tool view: Public", exception.getMessage());
    }

    @Test
    void testRejectsAgentToolViewInExposeInViews() {
        AcpProviderConfiguration config = new AcpProviderConfiguration(
                "copilot",
                "copilot/",
                "shared",
                List.of("public", "shared"),
                new AcpProcessConfiguration("fake-acp", List.of(), null, Map.of()),
                new AcpExecutionConfiguration());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AgentToolViewValidator.validate(config, Set.of("copilot")));

        assertEquals(
                "agentToolView must not be a view where the ACP provider is exposed (would cause recursion): "
                        + "provider=copilot agentToolView=shared exposeInViews=[public, shared]",
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
