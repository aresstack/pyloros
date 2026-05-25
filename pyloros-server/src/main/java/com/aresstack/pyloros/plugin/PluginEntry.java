package com.aresstack.pyloros.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Per-plugin entry inside the {@code plugins.items} configuration list.
 *
 * <p>{@link #enabled()} is intentionally a {@link Boolean} so that the resolver can
 * distinguish between "not specified" (fall back to {@code enabledByDefault}) and an
 * explicit {@code true}/{@code false} override.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PluginEntry(
        String id,
        Boolean enabled,
        Map<String, Object> configuration
) {
}
