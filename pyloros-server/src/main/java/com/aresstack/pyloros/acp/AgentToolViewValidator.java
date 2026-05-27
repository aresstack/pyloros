package com.aresstack.pyloros.acp;

import java.util.Objects;
import java.util.Map;
import java.util.Set;

/**
 * Validates that an ACP provider's agentToolView does not create recursion:
 * <ul>
 *   <li>Must not be 'public' — agents must never see the public view</li>
 *   <li>Must not reference the provider's own ID</li>
 *   <li>Must not reference another ACP provider ID</li>
 *   <li>Must not be a view where the ACP provider's own tools are exposed (prevents the agent from seeing itself)</li>
 *   <li>Must not be a view where any other ACP provider is exposed (prevents cross-agent recursion loops)</li>
 * </ul>
 */
public final class AgentToolViewValidator {

    private AgentToolViewValidator() {
    }

    /**
     * Validates that the given provider configuration does not create a recursive tool view.
     * @param config the ACP provider configuration
     * @param allAcpProviderIds set of all ACP provider IDs in the system
     * @param acpProviderIdsByExposedView ACP provider IDs grouped by exposed view name
     * @throws IllegalArgumentException if recursion is detected or public view is used
     */
    public static void validate(
            AcpProviderConfiguration config,
            Set<String> allAcpProviderIds,
            Map<String, Set<String>> acpProviderIdsByExposedView) {
        AcpProviderConfiguration providerConfig = Objects.requireNonNull(config, "config must not be null");
        Set<String> providerIds = Set.copyOf(Objects.requireNonNull(allAcpProviderIds, "allAcpProviderIds must not be null"));
        Map<String, Set<String>> providerIdsByView = Map.copyOf(
                Objects.requireNonNull(acpProviderIdsByExposedView, "acpProviderIdsByExposedView must not be null"));
        String agentToolView = providerConfig.agentToolView();
        String providerId = providerConfig.id();

        if ("public".equalsIgnoreCase(agentToolView)) {
            throw new IllegalArgumentException(
                    "Invalid agentToolView for ACP provider '" + providerId + "': '" + agentToolView + "' "
                            + "must not be 'public' because it would expose the public tool view to the agent "
                            + "(recursion risk).");
        }
        if (providerId.equals(agentToolView)) {
            throw new IllegalArgumentException(
                    "Invalid agentToolView for ACP provider '" + providerId + "': '" + agentToolView + "' "
                            + "references the same provider ID and would allow self-recursion.");
        }
        if (providerIds.contains(agentToolView)) {
            throw new IllegalArgumentException(
                    "Invalid agentToolView for ACP provider '" + providerId + "': '" + agentToolView + "' "
                            + "references another ACP provider ID and may trigger recursive agent delegation.");
        }
        if (providerConfig.exposeInViews().contains(agentToolView)) {
            throw new IllegalArgumentException(
                    "Invalid agentToolView for ACP provider '" + providerId + "': '" + agentToolView + "' "
                            + "collides with exposeInViews " + providerConfig.exposeInViews()
                            + " so the provider would see its own ACP tools (self-recursion risk).");
        }

        String collidingProvider = providerIdsByView.getOrDefault(agentToolView, Set.of()).stream()
                .filter(exposedProviderId -> !providerId.equals(exposedProviderId))
                .sorted()
                .findFirst()
                .orElse(null);
        if (collidingProvider != null) {
            throw new IllegalArgumentException(
                    "Invalid agentToolView for ACP provider '" + providerId + "': '" + agentToolView + "' "
                            + "includes ACP provider '" + collidingProvider + "' and may trigger recursive agent invocation.");
        }
    }
}
