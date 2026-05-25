package com.aresstack.pyloros.plugin;

/**
 * Context passed to plugins during initialization and contribution.
 *
 * <p>Release 4 (R4-04) will expand this contract with the concrete core services
 * exposed to plugins. For R4-06 (plugin error handling and diagnostics) the
 * context is intentionally minimal: it exists so the plugin lifecycle API is
 * stable across releases and so failing plugins can be exercised without
 * pulling in the full core wiring.</p>
 */
public interface PluginContext {

    /**
     * Empty default context used until R4-04 supplies the full set of core services.
     */
    PluginContext EMPTY = new PluginContext() {
    };
}
