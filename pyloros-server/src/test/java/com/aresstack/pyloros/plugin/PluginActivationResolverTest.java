package com.aresstack.pyloros.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginActivationResolverTest {

    @Test
    void pluginIsEnabledWhenExplicitlyEnabled() {
        PluginsConfig config = new PluginsConfig(Boolean.FALSE, List.of(
                new PluginEntry("example-tools", Boolean.TRUE, Map.of("prefix", "example/"))
        ));

        PluginActivation activation = new PluginActivationResolver(config).resolve("example-tools");

        assertTrue(activation.enabled());
        assertEquals("explicit-enabled", activation.reason());
        assertEquals("example/", activation.configuration().getString("prefix", ""));
    }

    @Test
    void pluginIsDisabledWhenExplicitlyDisabled() {
        PluginsConfig config = new PluginsConfig(Boolean.TRUE, List.of(
                new PluginEntry("example-tools", Boolean.FALSE, Map.of())
        ));

        PluginActivation activation = new PluginActivationResolver(config).resolve("example-tools");

        assertFalse(activation.enabled());
        assertEquals("explicit-disabled", activation.reason());
    }

    @Test
    void enabledByDefaultTrueActivatesUnconfiguredPlugins() {
        PluginsConfig config = new PluginsConfig(Boolean.TRUE, List.of());

        PluginActivation activation = new PluginActivationResolver(config).resolve("any-plugin");

        assertTrue(activation.enabled());
        assertEquals("default-enabled", activation.reason());
        assertTrue(activation.configuration().isEmpty());
    }

    @Test
    void enabledByDefaultFalseDeactivatesUnconfiguredPlugins() {
        PluginsConfig config = new PluginsConfig(Boolean.FALSE, List.of());

        PluginActivation activation = new PluginActivationResolver(config).resolve("any-plugin");

        assertFalse(activation.enabled());
        assertEquals("default-disabled", activation.reason());
    }

    @Test
    void entryWithoutExplicitEnabledFallsBackToEnabledByDefault() {
        PluginsConfig config = new PluginsConfig(Boolean.TRUE, List.of(
                new PluginEntry("example-tools", null, Map.of("prefix", "x/"))
        ));

        PluginActivation activation = new PluginActivationResolver(config).resolve("example-tools");

        assertTrue(activation.enabled());
        assertEquals("default-enabled", activation.reason());
        assertEquals("x/", activation.configuration().getString("prefix", ""));
    }

    @Test
    void pluginSpecificConfigurationIsPassedThrough() {
        PluginsConfig config = new PluginsConfig(Boolean.FALSE, List.of(
                new PluginEntry("example-tools", Boolean.TRUE, Map.of(
                        "prefix", "example/",
                        "timeoutMs", 250,
                        "verbose", true
                ))
        ));

        PluginConfiguration pluginConfig = new PluginActivationResolver(config)
                .resolve("example-tools")
                .configuration();

        assertEquals("example/", pluginConfig.requireString("prefix"));
        assertEquals(250, pluginConfig.requireInt("timeoutMs"));
        assertTrue(pluginConfig.requireBoolean("verbose"));
    }

    @Test
    void duplicatePluginIdIsReported() {
        PluginsConfig config = new PluginsConfig(Boolean.FALSE, List.of(
                new PluginEntry("dup", Boolean.TRUE, Map.of()),
                new PluginEntry("dup", Boolean.FALSE, Map.of())
        ));

        PluginConfigurationException exception = assertThrows(
                PluginConfigurationException.class,
                () -> new PluginActivationResolver(config));

        assertEquals("dup", exception.pluginId());
    }

    @Test
    void emptyConfigDefaultsToDisabled() {
        PluginActivationResolver resolver = new PluginActivationResolver(null);

        PluginActivation activation = resolver.resolve("anything");

        assertFalse(activation.enabled());
        assertEquals("default-disabled", activation.reason());
        assertTrue(resolver.config().effectiveItems().isEmpty());
        assertFalse(resolver.config().effectiveEnabledByDefault());
    }
}
