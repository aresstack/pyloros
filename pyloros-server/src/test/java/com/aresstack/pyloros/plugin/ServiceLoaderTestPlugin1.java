package com.aresstack.pyloros.plugin;

/**
 * Test plugin used by {@link ServiceLoaderDiscoveryTest} to verify that
 * {@link PluginRegistry} can discover plugins through a real
 * {@link java.util.ServiceLoader} lookup via {@code META-INF/services}.
 */
public final class ServiceLoaderTestPlugin1 implements PylorosPlugin {

    public ServiceLoaderTestPlugin1() {
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.of("test-plugin-1", "Test Plugin 1", "1.0.0");
    }

    @Override
    public PluginContribution contribute(PluginContext context) {
        return PluginContribution.empty();
    }
}
