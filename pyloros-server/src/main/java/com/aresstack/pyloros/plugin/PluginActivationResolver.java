package com.aresstack.pyloros.plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves whether a plugin should be enabled or disabled and exposes its
 * plugin-specific configuration block.
 *
 * <p>Resolution rules (R4-03 acceptance criteria):
 * <ul>
 *   <li>An entry whose {@code enabled} field is set wins over {@code enabledByDefault}.</li>
 *   <li>If no entry exists for the plugin id, the resolver falls back to
 *       {@code enabledByDefault} (which itself defaults to {@code false}).</li>
 *   <li>Disabled plugins still produce a {@link PluginActivation} so callers can log
 *       and surface the decision, but they are expected to contribute no providers.</li>
 *   <li>Missing optional configuration never throws &mdash; absent keys come back as
 *       {@link java.util.Optional#empty()} via {@link PluginConfiguration}. Required
 *       configuration is validated by the plugin itself using
 *       {@link PluginConfiguration#requireString(String)} and friends, which raise a
 *       {@link PluginConfigurationException}.</li>
 *   <li>Duplicate ids in {@code items} are reported via
 *       {@link PluginConfigurationException} so operators receive a clear diagnostic
 *       instead of silently shadowed configuration.</li>
 * </ul>
 *
 * <p>The resolver itself is immutable and thread-safe.
 */
public final class PluginActivationResolver {

    private final PluginsConfig config;
    private final Map<String, PluginEntry> entriesById;

    public PluginActivationResolver(PluginsConfig config) {
        this.config = config == null ? PluginsConfig.empty() : config;
        this.entriesById = indexEntries(this.config);
    }

    /** Configuration this resolver was constructed with (never {@code null}). */
    public PluginsConfig config() {
        return config;
    }

    /** Activation decision for the given plugin id. */
    public PluginActivation resolve(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId");
        if (pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }

        PluginEntry entry = entriesById.get(pluginId);
        if (entry == null) {
            boolean enabled = config.effectiveEnabledByDefault();
            String reason = enabled ? "default-enabled" : "default-disabled";
            return new PluginActivation(pluginId, enabled, reason, PluginConfiguration.empty(pluginId));
        }

        PluginConfiguration configuration = new PluginConfiguration(pluginId, entry.configuration());
        if (entry.enabled() != null) {
            boolean enabled = entry.enabled();
            String reason = enabled ? "explicit-enabled" : "explicit-disabled";
            return new PluginActivation(pluginId, enabled, reason, configuration);
        }

        boolean enabled = config.effectiveEnabledByDefault();
        String reason = enabled ? "default-enabled" : "default-disabled";
        return new PluginActivation(pluginId, enabled, reason, configuration);
    }

    private static Map<String, PluginEntry> indexEntries(PluginsConfig config) {
        Map<String, PluginEntry> result = new LinkedHashMap<>();
        for (PluginEntry entry : config.effectiveItems()) {
            if (entry == null) {
                continue;
            }
            String id = entry.id();
            if (id == null || id.isBlank()) {
                throw new PluginConfigurationException(null, "id",
                        "plugin entry is missing an id");
            }
            if (result.containsKey(id)) {
                throw new PluginConfigurationException(id, "id",
                        "duplicate plugin id in configuration");
            }
            result.put(id, entry);
        }
        return Map.copyOf(result);
    }
}
