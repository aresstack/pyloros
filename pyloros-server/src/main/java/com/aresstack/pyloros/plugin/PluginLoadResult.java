package com.aresstack.pyloros.plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostic record produced by the plugin loader for one discovered plugin.
 *
 * <p>Distinct from {@link PluginDescriptor}, which is the plugin's stable
 * metadata. A {@code PluginLoadResult} represents the host's outcome of trying
 * to load and use a plugin: status, optional structured error, and — when
 * available — the canonical {@link PluginDescriptor}.</p>
 *
 * <p>If the plugin could not be instantiated, no canonical descriptor is yet
 * available; {@link #descriptor()} returns {@link Optional#empty()} and
 * {@link #pluginId()} falls back to the implementation class name so operators
 * can still identify the source.</p>
 *
 * @param pluginId   stable plugin id (from {@link PluginDescriptor#id()}) when
 *                   available, otherwise the implementation class name; never
 *                   {@code null} or blank
 * @param status     current {@link PluginStatus}
 * @param descriptor canonical plugin descriptor when the plugin's
 *                   {@code descriptor()} call returned a valid value, otherwise
 *                   {@code null}
 * @param error      populated when {@code status} represents a failure
 */
public record PluginLoadResult(
        String pluginId,
        PluginStatus status,
        PluginDescriptor descriptor,
        PluginErrorInfo error) {

    public PluginLoadResult {
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

    public static PluginLoadResult loaded(PluginDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return new PluginLoadResult(descriptor.id(), PluginStatus.LOADED, descriptor, null);
    }

    public static PluginLoadResult disabled(PluginDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return new PluginLoadResult(descriptor.id(), PluginStatus.DISABLED, descriptor, null);
    }

    public static PluginLoadResult failedToLoad(String pluginId, Throwable cause) {
        return new PluginLoadResult(pluginId, PluginStatus.FAILED_TO_LOAD, null, PluginErrorInfo.from(cause));
    }

    public static PluginLoadResult failedToInitialize(PluginDescriptor descriptor, Throwable cause) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return new PluginLoadResult(
                descriptor.id(), PluginStatus.FAILED_TO_INITIALIZE, descriptor, PluginErrorInfo.from(cause));
    }

    public static PluginLoadResult failedToContribute(PluginDescriptor descriptor, Throwable cause) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return new PluginLoadResult(
                descriptor.id(), PluginStatus.FAILED_TO_CONTRIBUTE, descriptor, PluginErrorInfo.from(cause));
    }

    public Optional<PluginDescriptor> optionalDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    public Optional<PluginErrorInfo> errorInfo() {
        return Optional.ofNullable(error);
    }
}
