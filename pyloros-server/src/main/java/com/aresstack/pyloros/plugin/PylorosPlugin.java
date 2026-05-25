package com.aresstack.pyloros.plugin;

/**
 * Contract for Pyloros plugins.
 */
public interface PylorosPlugin {

    PluginDescriptor descriptor();

    default void initialize(PluginContext context) {
        // no-op by default
    }

    PluginContribution contribute(PluginContext context);
}
