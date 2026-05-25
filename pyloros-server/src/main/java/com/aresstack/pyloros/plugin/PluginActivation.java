package com.aresstack.pyloros.plugin;

/**
 * Decision produced by {@link PluginActivationResolver} for a given plugin id.
 *
 * <p>The {@link #reason()} field is meant for diagnostics/log lines and intentionally
 * uses a small, stable vocabulary so log scrapers and tests can rely on it.
 *
 * @param pluginId      id of the plugin this decision applies to
 * @param enabled       {@code true} if the plugin should contribute providers
 * @param reason        short, machine-friendly reason code (e.g. {@code "explicit-enabled"})
 * @param configuration plugin-specific configuration view (always non-null)
 */
public record PluginActivation(
        String pluginId,
        boolean enabled,
        String reason,
        PluginConfiguration configuration
) {

    public PluginActivation {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        if (configuration == null) {
            configuration = PluginConfiguration.empty(pluginId);
        }
    }
}
