package com.aresstack.pyloros.plugin;

/**
 * Allows a plugin to emit diagnostic messages associated with its plugin id.
 *
 * <p>This service is exposed to plugins via
 * {@link PluginContext#service(Class) PluginContext.service(PluginDiagnostics.class)}.
 * The host implementation routes messages to its logging infrastructure and
 * automatically tags them with the plugin id so that diagnostic messages are
 * traceable to their origin.
 *
 * <p>Plugins should use this service rather than a logging framework directly;
 * this allows the host to capture, filter, or redirect messages without
 * requiring changes to the plugin.
 *
 * @see PluginContext#service(Class)
 * @see PluginConfigurationView
 */
public interface PluginDiagnostics {

    /**
     * Emit an informational message.
     *
     * @param message the diagnostic message; must not be {@code null}
     */
    void info(String message);

    /**
     * Emit a warning message.
     *
     * @param message the diagnostic message; must not be {@code null}
     */
    void warn(String message);

    /**
     * Emit an error message.
     *
     * @param message the diagnostic message; must not be {@code null}
     */
    void error(String message);

    /**
     * A no-op {@link PluginDiagnostics} that discards all messages silently.
     * Useful for tests and minimal hosts that do not need to capture diagnostics.
     *
     * @return a shared no-op instance
     */
    static PluginDiagnostics noop() {
        return NoopPluginDiagnostics.INSTANCE;
    }
}
