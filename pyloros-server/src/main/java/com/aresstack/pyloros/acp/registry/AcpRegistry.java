package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Top-level ACP registry model containing schema version and agent list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcpRegistry(
        String version,
        List<RegistryAgent> agents
) {
}
