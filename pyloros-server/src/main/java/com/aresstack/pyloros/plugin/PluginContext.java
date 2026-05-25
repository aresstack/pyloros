package com.aresstack.pyloros.plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime context handed by the host to a {@link PylorosPlugin} during its
 * lifecycle.
 *
 * <p>The context is intentionally minimal so that plugin authors can compile
 * against a small, stable surface and so that the host can add capabilities
 * over time without breaking plugins. In particular, this interface does not
 * reference Vert.x, HTTP or JSON-RPC types.
 *
 * <h2>Extensibility</h2>
 * Additional host capabilities are exposed through a typed
 * {@link #service(Class)} lookup. The set of available service types is
 * defined by the host and may grow in a backwards compatible way; plugins
 * simply ask for the types they need and gracefully handle
 * {@link Optional#empty() empty} results when a service is unavailable.
 *
 * <p>Hosts that do not (yet) expose any services can use {@link #noop(String)}
 * to obtain a minimal context that only provides the plugin's
 * {@link #pluginId() identity}.
 *
 * <h2>Lifecycle</h2>
 * The host typically constructs a {@code PluginContext} once per plugin and
 * passes it to {@link PylorosPlugin#initialize(PluginContext)} and
 * {@link PylorosPlugin#contribute(PluginContext)}.
 */
public interface PluginContext {

    /**
     * Identity of the plugin this context belongs to, as declared by its
     * {@link PluginDescriptor#id()}.
     *
     * @return the plugin id; never {@code null} or blank
     */
    String pluginId();

    /**
     * Look up a host-provided service.
     *
     * <p>The set of supported {@code type}s is defined by the host. Plugins
     * should treat an {@link Optional#empty() empty} result as "this host
     * does not (yet) expose this capability" and degrade gracefully.
     *
     * @param type the service interface to look up; never {@code null}
     * @param <T>  the service type
     * @return the service implementation if available, otherwise
     *         {@link Optional#empty()}
     * @throws NullPointerException if {@code type} is {@code null}
     */
    <T> Optional<T> service(Class<T> type);

    /**
     * Create a minimal {@link PluginContext} that exposes only the given
     * plugin id and no host services. Useful for tests, for hosts that have
     * not yet wired any host-side services, and for plugins that only need
     * to know their own identity.
     *
     * @param pluginId the plugin id; must not be {@code null} or blank
     * @return a no-op context bound to {@code pluginId}
     * @throws NullPointerException     if {@code pluginId} is {@code null}
     * @throws IllegalArgumentException if {@code pluginId} is blank
     */
    static PluginContext noop(String pluginId) {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        if (pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        return new PluginContext() {
            @Override
            public String pluginId() {
                return pluginId;
            }

            @Override
            public <T> Optional<T> service(Class<T> type) {
                Objects.requireNonNull(type, "type must not be null");
                return Optional.empty();
            }

            @Override
            public String toString() {
                return "PluginContext.noop(" + pluginId + ")";
            }
        };
    }
}
