package com.aresstack.pyloros.plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only view of a single plugin's configuration, scoped to that plugin.
 *
 * <p>This service is exposed to plugins via
 * {@link PluginContext#service(Class) PluginContext.service(PluginConfigurationView.class)}
 * so that a plugin can read only its own configuration without access to
 * the raw {@link PluginConfiguration} or any other plugin's configuration block.
 *
 * <p>Optional accessors return empty / default values when a key is missing so
 * that absent optional configuration never aborts the server start. Required
 * accessors raise a {@link PluginConfigurationException} when a key is absent
 * or has the wrong type.
 *
 * <p>Implementations must be immutable and safe to share across threads.
 *
 * @see PluginContext#service(Class)
 * @see PluginDiagnostics
 */
public interface PluginConfigurationView {

    /** {@code true} if no configuration keys are present. */
    boolean isEmpty();

    /** {@code true} if the given key is present in the configuration. */
    boolean contains(String key);

    // ---------- optional accessors ----------

    Optional<String> getString(String key);

    String getString(String key, String defaultValue);

    Optional<Boolean> getBoolean(String key);

    boolean getBoolean(String key, boolean defaultValue);

    Optional<Integer> getInt(String key);

    int getInt(String key, int defaultValue);

    // ---------- required accessors ----------

    /**
     * @throws PluginConfigurationException if the key is missing or blank
     */
    String requireString(String key);

    /**
     * @throws PluginConfigurationException if the key is missing
     */
    boolean requireBoolean(String key);

    /**
     * @throws PluginConfigurationException if the key is missing or not a valid integer
     */
    int requireInt(String key);

    // ---------- factory ----------

    /**
     * Create a {@link PluginConfigurationView} backed by the given
     * {@link PluginConfiguration}.
     *
     * @param configuration the configuration to wrap; must not be {@code null}
     * @return a read-only view delegating to {@code configuration}
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    static PluginConfigurationView of(PluginConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return new PluginConfigurationView() {

            @Override
            public boolean isEmpty() {
                return configuration.isEmpty();
            }

            @Override
            public boolean contains(String key) {
                return configuration.contains(key);
            }

            @Override
            public Optional<String> getString(String key) {
                return configuration.getString(key);
            }

            @Override
            public String getString(String key, String defaultValue) {
                return configuration.getString(key, defaultValue);
            }

            @Override
            public Optional<Boolean> getBoolean(String key) {
                return configuration.getBoolean(key);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                return configuration.getBoolean(key, defaultValue);
            }

            @Override
            public Optional<Integer> getInt(String key) {
                return configuration.getInt(key);
            }

            @Override
            public int getInt(String key, int defaultValue) {
                return configuration.getInt(key, defaultValue);
            }

            @Override
            public String requireString(String key) {
                return configuration.requireString(key);
            }

            @Override
            public boolean requireBoolean(String key) {
                return configuration.requireBoolean(key);
            }

            @Override
            public int requireInt(String key) {
                return configuration.requireInt(key);
            }

            @Override
            public String toString() {
                return "PluginConfigurationView(" + configuration.pluginId() + ")";
            }
        };
    }
}
