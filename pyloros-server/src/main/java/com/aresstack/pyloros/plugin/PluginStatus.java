package com.aresstack.pyloros.plugin;

/**
 * Lifecycle status of a discovered plugin, used for operator diagnostics.
 */
public enum PluginStatus {

    /** Plugin loaded, initialized and contributed providers successfully. */
    LOADED,

    /** Plugin discovered but disabled by configuration. */
    DISABLED,

    /** Plugin could not be instantiated (constructor / ServiceLoader failure). */
    FAILED_TO_LOAD,

    /** {@code initialize(PluginContext)} threw. */
    FAILED_TO_INITIALIZE,

    /** {@code createToolProviders(PluginContext)} threw or returned an invalid contribution. */
    FAILED_TO_CONTRIBUTE
}
