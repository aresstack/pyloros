package com.aresstack.pyloros.plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable metadata about a {@link PylorosPlugin}.
 *
 * <p>A {@code PluginDescriptor} is the public identity of a plugin. It is
 * intentionally small and free of infrastructure concerns: no Vert.x, HTTP or
 * JSON-RPC types are referenced. Implementations may be provided as part of a
 * plugin JAR loaded via {@link java.util.ServiceLoader}.
 *
 * <p>The {@link #id() id} must be unique within a running Pyloros instance and
 * must be supplied by the plugin. {@link #name() name}, {@link #version()
 * version} and {@link #description() description} are optional and may be
 * empty.
 *
 * @param id          unique, non-blank plugin identifier (e.g.
 *                    {@code "com.example.my-plugin"})
 * @param name        human readable name; never {@code null}, may be empty
 * @param version     version string; never {@code null}, may be empty
 * @param description short description of the plugin; never {@code null}, may
 *                    be empty
 */
public record PluginDescriptor(String id, String name, String version, String description) {

    /**
     * Compact constructor that validates the descriptor.
     *
     * @throws NullPointerException     if {@code id}, {@code name},
     *                                  {@code version} or {@code description}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code id} is blank
     */
    public PluginDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("plugin id must not be blank");
        }
    }

    /**
     * Convenience factory for descriptors that only declare an id.
     *
     * @param id unique, non-blank plugin identifier
     * @return a descriptor with empty name, version and description
     */
    public static PluginDescriptor of(String id) {
        return new PluginDescriptor(id, "", "", "");
    }

    /**
     * Convenience factory for descriptors that declare id, name and version.
     *
     * @param id      unique, non-blank plugin identifier
     * @param name    human readable name
     * @param version version string
     * @return a descriptor with empty description
     */
    public static PluginDescriptor of(String id, String name, String version) {
        return new PluginDescriptor(id, name, version, "");
    }

    /**
     * @return the {@link #name()} wrapped in an {@link Optional}, empty if
     *         blank
     */
    public Optional<String> optionalName() {
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    /**
     * @return the {@link #version()} wrapped in an {@link Optional}, empty if
     *         blank
     */
    public Optional<String> optionalVersion() {
        return version.isBlank() ? Optional.empty() : Optional.of(version);
    }

    /**
     * @return the {@link #description()} wrapped in an {@link Optional}, empty
     *         if blank
     */
    public Optional<String> optionalDescription() {
        return description.isBlank() ? Optional.empty() : Optional.of(description);
    }
}
