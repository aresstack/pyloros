package com.aresstack.pyloros.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only view of a single plugin's configuration block.
 *
 * <p>Designed to be exposed via {@link PluginContext} to plugin implementations.
 * Optional accessors return empty/default values when a key is missing so that absent
 * optional configuration never aborts the server start. Required accessors raise a
 * {@link PluginConfigurationException} that captures the offending plugin id and key.
 *
 * <p>Instances are immutable and safe to share across threads.
 */
public final class PluginConfiguration {

    private final String pluginId;
    private final Map<String, Object> values;

    public PluginConfiguration(String pluginId, Map<String, Object> values) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.values = values == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Empty configuration for the given plugin id. */
    public static PluginConfiguration empty(String pluginId) {
        return new PluginConfiguration(pluginId, Map.of());
    }

    public String pluginId() {
        return pluginId;
    }

    /** Immutable view of the raw configuration map. */
    public Map<String, Object> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    // ---------- optional accessors ----------

    public Optional<String> getString(String key) {
        Object raw = values.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(raw));
    }

    public String getString(String key, String defaultValue) {
        return getString(key).orElse(defaultValue);
    }

    public Optional<Boolean> getBoolean(String key) {
        Object raw = values.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Boolean booleanValue) {
            return Optional.of(booleanValue);
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "false".equals(text)) {
            return Optional.of(Boolean.parseBoolean(text));
        }
        throw new PluginConfigurationException(pluginId, key,
                "expected boolean but got: " + raw);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key).orElse(defaultValue);
    }

    public Optional<Integer> getInt(String key) {
        Object raw = values.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Number number) {
            return Optional.of(number.intValue());
        }
        try {
            return Optional.of(Integer.parseInt(String.valueOf(raw).trim()));
        } catch (NumberFormatException cause) {
            throw new PluginConfigurationException(pluginId, key,
                    "expected integer but got: " + raw, cause);
        }
    }

    public int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    // ---------- required accessors ----------

    public String requireString(String key) {
        Object raw = values.get(key);
        if (raw == null) {
            throw new PluginConfigurationException(pluginId, key, "required value is missing");
        }
        String text = String.valueOf(raw);
        if (text.isBlank()) {
            throw new PluginConfigurationException(pluginId, key, "required value is blank");
        }
        return text;
    }

    public boolean requireBoolean(String key) {
        return getBoolean(key)
                .orElseThrow(() -> new PluginConfigurationException(pluginId, key,
                        "required value is missing"));
    }

    public int requireInt(String key) {
        return getInt(key)
                .orElseThrow(() -> new PluginConfigurationException(pluginId, key,
                        "required value is missing"));
    }
}
