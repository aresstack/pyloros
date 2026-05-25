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

    /**
     * Reserved for a future initialization phase that runs before
     * {@link PylorosPlugin#contribute()}. The canonical R4-01 API does not
     * expose such a hook yet; this value is retained so diagnostics stay
     * forward-compatible.
     */
    FAILED_TO_INITIALIZE,

    /**
     * {@link PylorosPlugin#contribute()} threw or returned an invalid
     * {@link PluginContribution}.
     */
    FAILED_TO_CONTRIBUTE
}
