package com.aresstack.pyloros.plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Host-side {@link PluginContext} implementation that exposes a controlled set
 * of services to a plugin via a typed service map.
 *
 * <p>The services exposed are intentionally limited to avoid leaking mutable
 * core internals, Vert.x routes, HTTP handlers, MCP transport, or raw
 * registries. Only {@link PluginConfigurationView} and {@link PluginDiagnostics}
 * are registered by the standard factory method
 * {@link #forPlugin(String, PluginConfiguration, PluginDiagnostics)}.
 *
 * <p>For tests and minimal hosts that do not require any services,
 * {@link PluginContext#noop(String)} remains available.
 */
final class HostPluginContext implements PluginContext {

    private final String pluginId;
    private final Map<Class<?>, Object> services;

    HostPluginContext(String pluginId, Map<Class<?>, Object> services) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId must not be null");
        if (pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        this.services = Map.copyOf(services);
    }

    @Override
    public String pluginId() {
        return pluginId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> service(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return Optional.ofNullable((T) services.get(type));
    }

    @Override
    public String toString() {
        return "HostPluginContext(" + pluginId + ", services=" + services.keySet() + ")";
    }

    /**
     * Create a {@link PluginContext} with the standard plugin services:
     * {@link PluginConfigurationView} and {@link PluginDiagnostics}.
     *
     * <p>The {@link PluginConfigurationView} is scoped to the given plugin's
     * configuration so that the plugin can only read its own keys.
     *
     * @param pluginId    the plugin id; must not be {@code null} or blank
     * @param config      the plugin's configuration; must not be {@code null}
     * @param diagnostics the plugin's diagnostics channel; must not be {@code null}
     * @return a context with the standard services registered
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code pluginId} is blank
     */
    static PluginContext forPlugin(
            String pluginId,
            PluginConfiguration config,
            PluginDiagnostics diagnostics) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        services.put(PluginConfigurationView.class, PluginConfigurationView.of(config));
        services.put(PluginDiagnostics.class, diagnostics);
        return new HostPluginContext(pluginId, services);
    }
}
