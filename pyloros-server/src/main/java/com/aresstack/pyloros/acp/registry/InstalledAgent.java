package com.aresstack.pyloros.acp.registry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents a persistently installed ACP registry agent.
 * Immutable record holding all metadata required to manage and launch the agent.
 */
public record InstalledAgent(
        String agentId,
        String installedVersion,
        String distributionType,
        String resolvedCommand,
        List<String> resolvedArgs,
        String installPath,
        String sourceRegistryVersion,
        String license,
        String configuredPrefix,
        String agentToolView,
        boolean enabled,
        Instant installedAt,
        Instant updatedAt
) {

    public InstalledAgent {
        agentId = requireText(agentId, "agentId");
        installedVersion = requireText(installedVersion, "installedVersion");
        distributionType = requireText(distributionType, "distributionType");
        resolvedCommand = requireText(resolvedCommand, "resolvedCommand");
        resolvedArgs = resolvedArgs == null ? List.of() : List.copyOf(resolvedArgs);
        installPath = installPath == null ? "" : installPath;
        sourceRegistryVersion = sourceRegistryVersion == null ? "" : sourceRegistryVersion;
        license = license == null ? "" : license;
        configuredPrefix = requireText(configuredPrefix, "configuredPrefix");
        agentToolView = requireText(agentToolView, "agentToolView");
        Objects.requireNonNull(installedAt, "installedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Returns a copy of this agent with the enabled flag set to the given value.
     */
    public InstalledAgent withEnabled(boolean enabled) {
        return new InstalledAgent(
                agentId, installedVersion, distributionType, resolvedCommand,
                resolvedArgs, installPath, sourceRegistryVersion, license,
                configuredPrefix, agentToolView, enabled, installedAt, Instant.now()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value.trim();
    }
}
