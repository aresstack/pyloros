package com.aresstack.pyloros.plugin;

/**
 * Thrown when a plugin's required configuration value is missing or has the wrong shape.
 *
 * <p>Carries the plugin id and (optionally) the configuration key that triggered the
 * problem so that operators get an actionable diagnostic.
 */
public class PluginConfigurationException extends RuntimeException {

    private final String pluginId;
    private final String configurationKey;

    public PluginConfigurationException(String pluginId, String configurationKey, String message) {
        super(formatMessage(pluginId, configurationKey, message));
        this.pluginId = pluginId;
        this.configurationKey = configurationKey;
    }

    public PluginConfigurationException(String pluginId, String configurationKey, String message, Throwable cause) {
        super(formatMessage(pluginId, configurationKey, message), cause);
        this.pluginId = pluginId;
        this.configurationKey = configurationKey;
    }

    public String pluginId() {
        return pluginId;
    }

    public String configurationKey() {
        return configurationKey;
    }

    private static String formatMessage(String pluginId, String configurationKey, String message) {
        StringBuilder builder = new StringBuilder("[PLUGIN-CONFIG]");
        if (pluginId != null && !pluginId.isBlank()) {
            builder.append(" plugin=").append(pluginId);
        }
        if (configurationKey != null && !configurationKey.isBlank()) {
            builder.append(" key=").append(configurationKey);
        }
        if (message != null && !message.isBlank()) {
            builder.append(" ").append(message);
        }
        return builder.toString();
    }
}
