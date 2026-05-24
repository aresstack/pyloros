package com.aresstack.pyloros.acp;

import java.util.Objects;
import java.util.Set;

/**
 * Validates that an ACP provider's agentToolView does not create recursion:
 * <ul>
 *   <li>Must not be 'public' — agents must never see the public view</li>
 *   <li>Must not reference the provider's own ID</li>
 *   <li>Must not reference another ACP provider ID</li>
 *   <li>Must not be a view where the ACP provider's own tools are exposed (prevents the agent from seeing itself)</li>
 * </ul>
 */
public final class AgentToolViewValidator {

    private AgentToolViewValidator() {
    }

    /**
     * Validates that the given provider configuration does not create a recursive tool view.
     * @param config the ACP provider configuration
     * @param allAcpProviderIds set of all ACP provider IDs in the system
     * @throws IllegalArgumentException if recursion is detected or public view is used
     */
    public static void validate(AcpProviderConfiguration config, Set<String> allAcpProviderIds) {
        AcpProviderConfiguration providerConfig = Objects.requireNonNull(config, "config must not be null");
        Set<String> providerIds = Set.copyOf(Objects.requireNonNull(allAcpProviderIds, "allAcpProviderIds must not be null"));
        String agentToolView = providerConfig.agentToolView();

        if ("public".equalsIgnoreCase(agentToolView)) {
            throw new IllegalArgumentException(
                    "agentToolView must not be 'public' — ACP agents must not see the public tool view: " + agentToolView);
        }
        if (providerConfig.id().equals(agentToolView)) {
            throw new IllegalArgumentException("agentToolView must not reference ACP provider itself: " + agentToolView);
        }
        if (providerIds.contains(agentToolView)) {
            throw new IllegalArgumentException("agentToolView must not reference another ACP provider: " + agentToolView);
        }
        if (providerConfig.exposeInViews().contains(agentToolView)) {
            throw new IllegalArgumentException(
                    "agentToolView must not be a view where the ACP provider is exposed (would cause recursion): "
                            + "provider=" + providerConfig.id() + " agentToolView=" + agentToolView
                            + " exposeInViews=" + providerConfig.exposeInViews());
        }
    }
}
