package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * JSON-serializable document for the installed agent store.
 * Contains a version field for forward-compatibility / migration support.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledAgentStoreDocument(
        int version,
        List<InstalledAgentEntry> agents
) {

    public static final int CURRENT_VERSION = 1;

    public InstalledAgentStoreDocument {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }

    public InstalledAgentStoreDocument() {
        this(CURRENT_VERSION, List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstalledAgentEntry(
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
            String installedAt,
            String updatedAt
    ) {
    }
}
