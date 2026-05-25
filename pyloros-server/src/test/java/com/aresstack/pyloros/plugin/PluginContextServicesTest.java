package com.aresstack.pyloros.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for R4-04: PluginContext host services — PluginConfigurationView and
 * PluginDiagnostics exposed through the typed service lookup.
 */
class PluginContextServicesTest {

    // -------------------------------------------------------------------------
    // PluginConfigurationView — own config readable
    // -------------------------------------------------------------------------

    /**
     * Acceptance: a plugin can obtain its own configuration view from the context.
     */
    @Test
    void pluginCanReadOwnConfiguration() {
        PluginConfiguration config = new PluginConfiguration(
                "com.example.alpha",
                Map.of("host", "localhost", "port", 8080));

        PluginContext context = HostPluginContext.forPlugin(
                "com.example.alpha", config, PluginDiagnostics.noop());

        Optional<PluginConfigurationView> view =
                context.service(PluginConfigurationView.class);

        assertTrue(view.isPresent(), "PluginConfigurationView must be available");
        assertEquals("localhost", view.get().requireString("host"));
        assertEquals(8080, view.get().requireInt("port"));
    }

    /**
     * Acceptance: the configuration view is scoped to the plugin — it cannot
     * see another plugin's keys that were never registered for it.
     */
    @Test
    void pluginCannotReadForeignPluginConfiguration() {
        // alpha-plugin gets its own config with key "secret"
        PluginConfiguration alphaConfig = new PluginConfiguration(
                "com.example.alpha",
                Map.of("secret", "alpha-only"));

        // beta-plugin gets an empty config — "secret" is NOT present
        PluginConfiguration betaConfig = PluginConfiguration.empty("com.example.beta");

        PluginContext betaContext = HostPluginContext.forPlugin(
                "com.example.beta", betaConfig, PluginDiagnostics.noop());

        Optional<PluginConfigurationView> betaView =
                betaContext.service(PluginConfigurationView.class);

        assertTrue(betaView.isPresent());
        // beta cannot read alpha's "secret"
        assertTrue(betaView.get().getString("secret").isEmpty(),
                "beta must not see alpha's configuration key");
        assertFalse(betaView.get().contains("secret"));
    }

    /**
     * Acceptance: context remains stable (empty configuration, no exception)
     * when a plugin has no configuration entry.
     */
    @Test
    void missingConfigurationIsStableAndEmpty() {
        PluginContext context = HostPluginContext.forPlugin(
                "com.example.no-config",
                PluginConfiguration.empty("com.example.no-config"),
                PluginDiagnostics.noop());

        Optional<PluginConfigurationView> view =
                context.service(PluginConfigurationView.class);

        assertTrue(view.isPresent());
        assertTrue(view.get().isEmpty());
        assertTrue(view.get().getString("anything").isEmpty());
        assertEquals("fallback", view.get().getString("anything", "fallback"));
        assertEquals(Optional.empty(), view.get().getInt("anything"));
        assertEquals(42, view.get().getInt("anything", 42));
        assertEquals(Optional.empty(), view.get().getBoolean("anything"));
        assertFalse(view.get().getBoolean("anything", false));
        assertFalse(view.get().contains("anything"));

        assertThrows(PluginConfigurationException.class,
                () -> view.get().requireString("must-fail"));
    }

    // -------------------------------------------------------------------------
    // PluginDiagnostics — messages recorded with plugin id
    // -------------------------------------------------------------------------

    /**
     * Acceptance: a plugin can emit info/warn/error messages via the diagnostics
     * service, and messages are associated with the plugin's id.
     */
    @Test
    void pluginCanEmitDiagnosticMessages() {
        List<String> recorded = new ArrayList<>();

        PluginDiagnostics diagnostics = new PluginDiagnostics() {
            @Override
            public void info(String message) {
                recorded.add("INFO:" + message);
            }

            @Override
            public void warn(String message) {
                recorded.add("WARN:" + message);
            }

            @Override
            public void error(String message) {
                recorded.add("ERROR:" + message);
            }
        };

        PluginContext context = HostPluginContext.forPlugin(
                "com.example.diag",
                PluginConfiguration.empty("com.example.diag"),
                diagnostics);

        Optional<PluginDiagnostics> diag = context.service(PluginDiagnostics.class);

        assertTrue(diag.isPresent(), "PluginDiagnostics must be available");
        diag.get().info("startup complete");
        diag.get().warn("low memory");
        diag.get().error("unexpected failure");

        assertEquals(3, recorded.size());
        assertEquals("INFO:startup complete", recorded.get(0));
        assertEquals("WARN:low memory", recorded.get(1));
        assertEquals("ERROR:unexpected failure", recorded.get(2));
    }

    /**
     * Acceptance: PluginDiagnostics.noop() discards messages without throwing.
     */
    @Test
    void noopDiagnosticsDiscardsAllMessages() {
        PluginDiagnostics noop = PluginDiagnostics.noop();
        assertNotNull(noop);
        // Must not throw for any input
        noop.info("anything");
        noop.warn("anything");
        noop.error("anything");
        // Multiple calls to noop() return the same instance
        assertSame(PluginDiagnostics.noop(), PluginDiagnostics.noop());
    }

    // -------------------------------------------------------------------------
    // HostPluginContext — service lookup and contract
    // -------------------------------------------------------------------------

    /**
     * Acceptance: HostPluginContext exposes the plugin id and provides both
     * standard services.
     */
    @Test
    void hostContextExposesPluginIdAndBothServices() {
        PluginContext context = HostPluginContext.forPlugin(
                "com.example.full",
                PluginConfiguration.empty("com.example.full"),
                PluginDiagnostics.noop());

        assertEquals("com.example.full", context.pluginId());
        assertTrue(context.service(PluginConfigurationView.class).isPresent());
        assertTrue(context.service(PluginDiagnostics.class).isPresent());
        assertTrue(context.service(Runnable.class).isEmpty(),
                "non-registered service type must be absent");
    }

    /**
     * Acceptance: service(null) throws NullPointerException.
     */
    @Test
    void hostContextRejectsNullServiceType() {
        PluginContext context = HostPluginContext.forPlugin(
                "com.example.npe",
                PluginConfiguration.empty("com.example.npe"),
                PluginDiagnostics.noop());

        assertThrows(NullPointerException.class, () -> context.service(null));
    }

    /**
     * Acceptance: HostPluginContext rejects blank plugin ids.
     */
    @Test
    void hostContextRejectsBlankPluginId() {
        assertThrows(NullPointerException.class,
                () -> HostPluginContext.forPlugin(null,
                        PluginConfiguration.empty("x"), PluginDiagnostics.noop()));
        assertThrows(IllegalArgumentException.class,
                () -> HostPluginContext.forPlugin("   ",
                        PluginConfiguration.empty("x"), PluginDiagnostics.noop()));
        assertThrows(NullPointerException.class,
                () -> HostPluginContext.forPlugin("x", null, PluginDiagnostics.noop()));
        assertThrows(NullPointerException.class,
                () -> HostPluginContext.forPlugin("x",
                        PluginConfiguration.empty("x"), null));
    }

    // -------------------------------------------------------------------------
    // PluginConfigurationView — no mutable internals exposed
    // -------------------------------------------------------------------------

    /**
     * Acceptance: the configuration view does not expose the raw map or any
     * mutable internal state.
     */
    @Test
    void configurationViewExposesNoMutableState() {
        PluginConfigurationView view = PluginConfigurationView.of(
                new PluginConfiguration("x", Map.of("k", "v")));

        // PluginConfigurationView does not have an asMap() method that would
        // allow mutation — verify only read-only accessors are available.
        assertTrue(view.contains("k"));
        assertEquals("v", view.requireString("k"));
        // The interface itself has no method returning a Map, so there is
        // nothing more to assert here — the type system enforces it.
    }

    /**
     * Acceptance: PluginConfigurationView.of(null) throws NullPointerException.
     */
    @Test
    void configurationViewRejectsNullConfiguration() {
        assertThrows(NullPointerException.class,
                () -> PluginConfigurationView.of(null));
    }

    // -------------------------------------------------------------------------
    // PluginRegistry integration — resolver overload
    // -------------------------------------------------------------------------

    /**
     * Acceptance: PluginRegistry.loadFrom with a PluginActivationResolver passes
     * the correct configuration view to the plugin context.
     */
    @Test
    void pluginRegistryWithResolverPassesConfigToPlugin() {
        // Arrange: a plugin that reads its own config during initialize()
        List<String> capturedValues = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<PluginContext> capturedContext =
                new java.util.concurrent.atomic.AtomicReference<>();

        PylorosPlugin plugin = new PylorosPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return PluginDescriptor.of("config-reader", "Config Reader", "1.0.0");
            }

            @Override
            public void initialize(PluginContext context) {
                capturedContext.set(context);
                context.service(PluginConfigurationView.class).ifPresent(view ->
                        capturedValues.add(view.getString("greeting", "none")));
            }

            @Override
            public PluginContribution contribute(PluginContext context) {
                return PluginContribution.empty();
            }
        };

        PluginsConfig config = new PluginsConfig(
                Boolean.TRUE,
                List.of(new PluginEntry("config-reader", true,
                        Map.of("greeting", "hello-from-config"))));

        PluginActivationResolver resolver = new PluginActivationResolver(config);

        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(serviceLoaderProvider(plugin)),
                resolver);

        // Assert
        assertEquals(1, registry.results().size());
        assertEquals(PluginStatus.LOADED, registry.results().get(0).status());
        assertNotNull(capturedContext.get());
        assertEquals(List.of("hello-from-config"), capturedValues,
                "Plugin must have received its own config via PluginConfigurationView");
    }

    /**
     * Acceptance: a plugin disabled by the resolver is not initialized and
     * receives no context.
     */
    @Test
    void pluginRegistryWithResolverDisablesPluginWhenConfiguredSo() {
        java.util.concurrent.atomic.AtomicBoolean initializeCalled =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        PylorosPlugin plugin = new PylorosPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return PluginDescriptor.of("disabled-by-resolver");
            }

            @Override
            public void initialize(PluginContext context) {
                initializeCalled.set(true);
            }

            @Override
            public PluginContribution contribute(PluginContext context) {
                return PluginContribution.empty();
            }
        };

        PluginsConfig config = new PluginsConfig(
                Boolean.FALSE,
                List.of(new PluginEntry("disabled-by-resolver", false, null)));
        PluginActivationResolver resolver = new PluginActivationResolver(config);

        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(serviceLoaderProvider(plugin)),
                resolver);

        assertEquals(PluginStatus.DISABLED, registry.results().get(0).status());
        assertFalse(initializeCalled.get(), "initialize must not be called for disabled plugin");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static <P extends PylorosPlugin> java.util.ServiceLoader.Provider<P>
            serviceLoaderProvider(P instance) {
        return new java.util.ServiceLoader.Provider<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends P> type() {
                return (Class<? extends P>) instance.getClass();
            }

            @Override
            public P get() {
                return instance;
            }
        };
    }
}
