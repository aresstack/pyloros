package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.List;

/**
 * Extension point loaded via {@link java.util.ServiceLoader}.
 *
 * <p>A plugin goes through three lifecycle phases that are diagnosed
 * independently by {@link PluginRegistry}:</p>
 * <ol>
 *   <li><b>load</b> – the implementation class is instantiated by the
 *       {@code ServiceLoader}. A failure here is reported as
 *       {@link PluginStatus#FAILED_TO_LOAD}.</li>
 *   <li><b>initialize</b> – {@link #initialize(PluginContext)} runs once
 *       after construction. A failure here is reported as
 *       {@link PluginStatus#FAILED_TO_INITIALIZE}.</li>
 *   <li><b>contribute</b> – {@link #createToolProviders(PluginContext)} is
 *       called and the returned providers are validated. A failure (thrown
 *       exception, {@code null} list, {@code null} provider or invalid
 *       {@code providerId}) is reported as
 *       {@link PluginStatus#FAILED_TO_CONTRIBUTE} and the partial contribution
 *       is dropped to keep the registry consistent.</li>
 * </ol>
 */
public interface PylorosPlugin {

    /**
     * Stable identifier for this plugin, used for diagnostics and enable/disable
     * configuration. Must not be {@code null} or blank.
     */
    String getPluginId();

    /**
     * Optional initialization hook invoked once before {@link #createToolProviders}.
     * Implementations may perform setup work or fail fast here.
     */
    default void initialize(PluginContext context) {
        // no-op by default
    }

    /**
     * Returns the {@link ToolProvider} contributions of this plugin. May return
     * an empty list. Must not return {@code null}, and each provider must
     * return a non-blank {@link ToolProvider#providerId()}.
     */
    List<ToolProvider> createToolProviders(PluginContext context);
}
