package com.aresstack.pyloros.plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable diagnostic record for one discovered plugin.
 *
 * <p>For plugins that could not even be instantiated, {@link #pluginId()} falls
 * back to the implementation class name so operators can still identify the
 * source.</p>
 *
 * @param pluginId stable plugin id or fallback class name when load failed
 * @param status   current {@link PluginStatus}
 * @param error    populated when {@code status} represents a failure
 */
public record PluginDescriptor(String pluginId, PluginStatus status, PluginErrorInfo error) {

    public PluginDescriptor {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        if (pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        boolean isFailure = status == PluginStatus.FAILED_TO_LOAD
                || status == PluginStatus.FAILED_TO_INITIALIZE
                || status == PluginStatus.FAILED_TO_CONTRIBUTE;
        if (isFailure && error == null) {
            throw new IllegalArgumentException("error must be provided for failure status " + status);
        }
        if (!isFailure && error != null) {
            throw new IllegalArgumentException("error must be null for non-failure status " + status);
        }
    }

    public static PluginDescriptor loaded(String pluginId) {
        return new PluginDescriptor(pluginId, PluginStatus.LOADED, null);
    }

    public static PluginDescriptor disabled(String pluginId) {
        return new PluginDescriptor(pluginId, PluginStatus.DISABLED, null);
    }

    public static PluginDescriptor failedToLoad(String pluginId, Throwable cause) {
        return new PluginDescriptor(pluginId, PluginStatus.FAILED_TO_LOAD, PluginErrorInfo.from(cause));
    }

    public static PluginDescriptor failedToInitialize(String pluginId, Throwable cause) {
        return new PluginDescriptor(pluginId, PluginStatus.FAILED_TO_INITIALIZE, PluginErrorInfo.from(cause));
    }

    public static PluginDescriptor failedToContribute(String pluginId, Throwable cause) {
        return new PluginDescriptor(pluginId, PluginStatus.FAILED_TO_CONTRIBUTE, PluginErrorInfo.from(cause));
    }

    public Optional<PluginErrorInfo> errorInfo() {
        return Optional.ofNullable(error);
    }
}
