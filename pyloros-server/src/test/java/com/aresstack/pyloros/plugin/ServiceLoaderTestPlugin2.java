package com.aresstack.pyloros.plugin;

/**
 * Second test plugin used by {@link ServiceLoaderDiscoveryTest} to verify that
 * multiple plugins are discovered via a real {@link java.util.ServiceLoader}.
 */
public final class ServiceLoaderTestPlugin2 implements PylorosPlugin {

    public ServiceLoaderTestPlugin2() {
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.of("test-plugin-2", "Test Plugin 2", "1.0.0");
    }

    @Override
    public PluginContribution contribute(PluginContext context) {
        return PluginContribution.empty();
    }
}
