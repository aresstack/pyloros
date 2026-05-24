package com.aresstack.pyloros.acp;

import java.util.Objects;
import java.util.Set;

/**
 * Validates that an ACP provider's agentToolView does not contain the provider itself
 * (preventing recursion) and does not contain other ACP providers that could cause
 * indirect recursion.
 */
public final class AgentToolViewValidator {

    private AgentToolViewValidator() {
    }

    /**
     * Validates that the given provider configuration does not create a recursive tool view.
     * @param config the ACP provider configuration
     * @param allAcpProviderIds set of all ACP provider IDs in the system
     * @throws IllegalArgumentException if recursion is detected
     */
    public static void validate(AcpProviderConfiguration config, Set<String> allAcpProviderIds) {
        AcpProviderConfiguration providerConfig = Objects.requireNonNull(config, "config must not be null");
        Set<String> providerIds = Set.copyOf(Objects.requireNonNull(allAcpProviderIds, "allAcpProviderIds must not be null"));
        String agentToolView = providerConfig.agentToolView();
        if (providerConfig.id().equals(agentToolView)) {
            throw new IllegalArgumentException("agentToolView must not reference ACP provider itself: " + agentToolView);
        }
        if (providerIds.contains(agentToolView)) {
            throw new IllegalArgumentException("agentToolView must not reference another ACP provider: " + agentToolView);
        }
    }
}
