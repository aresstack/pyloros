package com.aresstack.pyloros.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Root configuration object for the plugin subsystem.
 *
 * <p>Mirrors the JSON shape documented in the R4-03 specification:
 * <pre>{@code
 * {
 *   "plugins": {
 *     "enabledByDefault": false,
 *     "items": [
 *       { "id": "example-tools", "enabled": true, "configuration": { "prefix": "example/" } }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>Both fields are tolerant of {@code null}: {@link #enabledByDefault()} defaults to
 * {@code Boolean.FALSE} via {@link #effectiveEnabledByDefault()} and missing items are
 * treated as an empty list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PluginsConfig(
        Boolean enabledByDefault,
        List<PluginEntry> items
) {

    /** Default configuration: nothing enabled and no entries. */
    public static PluginsConfig empty() {
        return new PluginsConfig(Boolean.FALSE, List.of());
    }

    /** {@code true} if plugins without an explicit entry should be enabled by default. */
    public boolean effectiveEnabledByDefault() {
        return enabledByDefault != null && enabledByDefault;
    }

    /** Never-null view of {@link #items()}. */
    public List<PluginEntry> effectiveItems() {
        return items == null ? List.of() : items;
    }
}
