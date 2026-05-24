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
