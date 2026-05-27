package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Distribution options for an ACP registry agent.
 * Supports binary, npx, and uvx distribution types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistryDistribution(
        Map<String, BinaryTarget> binary,
        PackageDistribution npx,
        PackageDistribution uvx
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BinaryTarget(
            String archive,
            String cmd,
            List<String> args,
            Map<String, String> env
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PackageDistribution(
            @JsonProperty("package") String packageName,
            List<String> args,
            Map<String, String> env
    ) {
    }
}
