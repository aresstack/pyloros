package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Metadata for a single agent in the ACP registry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistryAgent(
        String id,
        String name,
        String version,
        String description,
        String repository,
        String website,
        List<String> authors,
        String license,
        String icon,
        RegistryDistribution distribution
) {
}
