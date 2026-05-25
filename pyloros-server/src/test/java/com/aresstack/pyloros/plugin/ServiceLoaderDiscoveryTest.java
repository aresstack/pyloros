package com.aresstack.pyloros.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that {@link PluginRegistry} discovers plugins through a real
 * {@link java.util.ServiceLoader} lookup, using the
 * {@code META-INF/services/com.aresstack.pyloros.plugin.PylorosPlugin} file in
 * the test resources.
 *
 * <p>Covers R4-02 acceptance criteria:
 * <ul>
 *   <li>ein gültiges Testplugin wird geladen (single plugin via real ServiceLoader)</li>
 *   <li>mehrere Testplugins werden geladen (multiple plugins)</li>
 *   <li>resolver-backed enable/disable behavior</li>
 * </ul>
 */
class ServiceLoaderDiscoveryTest {

    /**
     * A single valid plugin listed in {@code META-INF/services} is discovered
     * and transitions to {@link PluginStatus#LOADED}.
     */
    @Test
    void realServiceLoaderDiscoversBothRegisteredTestPlugins() {
        PluginRegistry registry = PluginRegistry.load(Set.of());

        List<String> loadedIds = registry.results().stream()
                .filter(r -> r.status() == PluginStatus.LOADED)
                .map(PluginLoadResult::pluginId)
                .toList();

        assertTrue(loadedIds.contains("test-plugin-1"),
                "ServiceLoader must discover ServiceLoaderTestPlugin1");
        assertTrue(loadedIds.contains("test-plugin-2"),
                "ServiceLoader must discover ServiceLoaderTestPlugin2");
    }

    /**
     * Multiple plugins listed in the same {@code META-INF/services} file are all
     * discovered and loaded without interfering with each other.
     */
    @Test
    void realServiceLoaderDiscoversMultiplePluginsIndependently() {
        PluginRegistry registry = PluginRegistry.load(Set.of());

        PluginLoadResult plugin1 = registry.findById("test-plugin-1").orElseThrow(
                () -> new AssertionError("test-plugin-1 not found in registry"));
        PluginLoadResult plugin2 = registry.findById("test-plugin-2").orElseThrow(
                () -> new AssertionError("test-plugin-2 not found in registry"));

        assertEquals(PluginStatus.LOADED, plugin1.status());
        assertNotNull(plugin1.descriptor());

        assertEquals(PluginStatus.LOADED, plugin2.status());
        assertNotNull(plugin2.descriptor());
    }

    /**
     * When a {@link PluginActivationResolver} explicitly enables one plugin and
     * leaves the other to the default (disabled), only the enabled plugin is loaded.
     */
    @Test
    void realServiceLoaderWithResolverEnablesOnlyExplicitlyEnabledPlugin() {
        PluginsConfig config = new PluginsConfig(
                Boolean.FALSE,
                List.of(new PluginEntry("test-plugin-1", Boolean.TRUE, Map.of()))
        );
        PluginActivationResolver resolver = new PluginActivationResolver(config);

        PluginRegistry registry = PluginRegistry.load(resolver);

        PluginLoadResult plugin1 = registry.findById("test-plugin-1").orElseThrow();
        PluginLoadResult plugin2 = registry.findById("test-plugin-2").orElseThrow();

        assertEquals(PluginStatus.LOADED, plugin1.status(),
                "test-plugin-1 must be loaded when explicitly enabled");
        assertEquals(PluginStatus.DISABLED, plugin2.status(),
                "test-plugin-2 must be disabled when enabledByDefault=false and no explicit entry");
    }

    /**
     * When {@code enabledByDefault=true} all discovered plugins are enabled, and
     * an explicit {@code enabled=false} entry for a specific plugin disables it.
     */
    @Test
    void realServiceLoaderWithResolverDisablesExplicitlyDisabledPlugin() {
        PluginsConfig config = new PluginsConfig(
                Boolean.TRUE,
                List.of(new PluginEntry("test-plugin-1", Boolean.FALSE, Map.of()))
        );
        PluginActivationResolver resolver = new PluginActivationResolver(config);

        PluginRegistry registry = PluginRegistry.load(resolver);

        PluginLoadResult plugin1 = registry.findById("test-plugin-1").orElseThrow();
        PluginLoadResult plugin2 = registry.findById("test-plugin-2").orElseThrow();

        assertEquals(PluginStatus.DISABLED, plugin1.status(),
                "test-plugin-1 must be disabled when explicitly set enabled=false");
        assertEquals(PluginStatus.LOADED, plugin2.status(),
                "test-plugin-2 must be loaded when enabledByDefault=true");
        assertFalse(registry.contributedProviders().stream()
                .anyMatch(p -> p.providerId().startsWith("test-plugin-1")));
    }
}
