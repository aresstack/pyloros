package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.tool.ToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

    @Test
    void loadsSuccessfulPluginAndExposesContributedProvider() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(WorkingPlugin.class, WorkingPlugin::new)),
                PluginContext.EMPTY,
                Set.of()
        );

        assertEquals(1, registry.descriptors().size());
        PluginDescriptor descriptor = registry.descriptors().get(0);
        assertEquals("working", descriptor.pluginId());
        assertEquals(PluginStatus.LOADED, descriptor.status());
        assertNull(descriptor.error());

        assertEquals(1, registry.contributedProviders().size());
        assertEquals("working-provider", registry.contributedProviders().get(0).providerId());
    }

    @Test
    void reportsDisabledPluginAsDisabledAndSkipsContribution() {
        WorkingPlugin instance = new WorkingPlugin();
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(WorkingPlugin.class, () -> instance)),
                PluginContext.EMPTY,
                Set.of("working")
        );

        assertEquals(PluginStatus.DISABLED, registry.findById("working").orElseThrow().status());
        assertTrue(registry.contributedProviders().isEmpty(), "disabled plugin must not contribute");
        assertFalse(instance.initialized, "disabled plugin must not be initialized");
        assertFalse(instance.contributed, "disabled plugin must not be asked for providers");
    }

    @Test
    void reportsFailedToLoadWhenConstructorThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(ConstructorThrowingPlugin.class, () -> {
                    throw new IllegalStateException("ctor boom");
                })),
                PluginContext.EMPTY,
                Set.of()
        );

        PluginDescriptor descriptor = registry.descriptors().get(0);
        assertEquals(PluginStatus.FAILED_TO_LOAD, descriptor.status());
        assertEquals(ConstructorThrowingPlugin.class.getName(), descriptor.pluginId());
        assertNotNull(descriptor.error());
        assertEquals(IllegalStateException.class.getName(), descriptor.error().errorClass());
        assertTrue(registry.contributedProviders().isEmpty());
    }

    @Test
    void reportsFailedToInitializeWhenInitThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(InitThrowingPlugin.class, InitThrowingPlugin::new)),
                PluginContext.EMPTY,
                Set.of()
        );

        PluginDescriptor descriptor = registry.descriptors().get(0);
        assertEquals("init-throws", descriptor.pluginId());
        assertEquals(PluginStatus.FAILED_TO_INITIALIZE, descriptor.status());
        assertEquals(RuntimeException.class.getName(), descriptor.error().errorClass());
        assertTrue(descriptor.error().message().contains("init boom"));
        assertTrue(registry.contributedProviders().isEmpty());
    }

    @Test
    void reportsFailedToContributeAndDropsPartialProvidersWhenContributionInvalid() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(InvalidContributionPlugin.class, InvalidContributionPlugin::new)),
                PluginContext.EMPTY,
                Set.of()
        );

        PluginDescriptor descriptor = registry.descriptors().get(0);
        assertEquals("invalid-contribution", descriptor.pluginId());
        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, descriptor.status());
        assertNotNull(descriptor.error());
        assertTrue(registry.contributedProviders().isEmpty(),
                "partial contribution from a failing plugin must not be published");
    }

    @Test
    void reportsFailedToContributeWhenCreateToolProvidersThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(ContributeThrowingPlugin.class, ContributeThrowingPlugin::new)),
                PluginContext.EMPTY,
                Set.of()
        );

        PluginDescriptor descriptor = registry.descriptors().get(0);
        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, descriptor.status());
        assertTrue(descriptor.error().message().contains("contribute boom"));
        assertTrue(registry.contributedProviders().isEmpty());
    }

    @Test
    void mixedSetIsolatesFailuresFromHealthyPlugins() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(
                        candidate(ConstructorThrowingPlugin.class, () -> {
                            throw new RuntimeException("nope");
                        }),
                        candidate(WorkingPlugin.class, WorkingPlugin::new),
                        candidate(ContributeThrowingPlugin.class, ContributeThrowingPlugin::new)
                ),
                PluginContext.EMPTY,
                Set.of()
        );

        assertEquals(3, registry.descriptors().size());
        assertEquals(PluginStatus.FAILED_TO_LOAD, registry.descriptors().get(0).status());
        assertEquals(PluginStatus.LOADED, registry.descriptors().get(1).status());
        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, registry.descriptors().get(2).status());

        // Only the working plugin's providers are exposed.
        assertEquals(List.of("working-provider"),
                registry.contributedProviders().stream().map(ToolProvider::providerId).toList());
    }

    @Test
    void truncatesVeryLongErrorMessages() {
        String veryLong = "x".repeat(PluginErrorInfo.MAX_MESSAGE_LENGTH * 3);
        PluginErrorInfo info = PluginErrorInfo.from(new RuntimeException(veryLong));

        assertEquals(PluginErrorInfo.MAX_MESSAGE_LENGTH, info.message().length());
        assertTrue(info.message().endsWith("..."));
        assertTrue(info.message().startsWith("xxxx"));
    }

    @Test
    void doesNotTruncateShortMessages() {
        PluginErrorInfo info = PluginErrorInfo.from(new RuntimeException("short"));
        assertEquals("short", info.message());
    }

    // ---- helpers / test plugin doubles ----

    private static <P extends PylorosPlugin> ServiceLoader.Provider<P> candidate(
            Class<P> type, Supplier<P> factory) {
        return new ServiceLoader.Provider<P>() {
            @Override
            public Class<? extends P> type() {
                return type;
            }

            @Override
            public P get() {
                return factory.get();
            }
        };
    }

    static final class WorkingPlugin implements PylorosPlugin {
        boolean initialized;
        boolean contributed;

        @Override
        public String getPluginId() {
            return "working";
        }

        @Override
        public void initialize(PluginContext context) {
            initialized = true;
        }

        @Override
        public List<ToolProvider> createToolProviders(PluginContext context) {
            contributed = true;
            return List.of(new NoopProvider("working-provider"));
        }
    }

    static final class ConstructorThrowingPlugin implements PylorosPlugin {
        @Override
        public String getPluginId() {
            return "ctor-throws";
        }

        @Override
        public List<ToolProvider> createToolProviders(PluginContext context) {
            return List.of();
        }
    }

    static final class InitThrowingPlugin implements PylorosPlugin {
        @Override
        public String getPluginId() {
            return "init-throws";
        }

        @Override
        public void initialize(PluginContext context) {
            throw new RuntimeException("init boom");
        }

        @Override
        public List<ToolProvider> createToolProviders(PluginContext context) {
            return List.of();
        }
    }

    static final class ContributeThrowingPlugin implements PylorosPlugin {
        @Override
        public String getPluginId() {
            return "contribute-throws";
        }

        @Override
        public List<ToolProvider> createToolProviders(PluginContext context) {
            throw new RuntimeException("contribute boom");
        }
    }

    /**
     * Returns a list that contains one valid provider and one provider with a
     * blank providerId. The whole contribution must be rejected so that the
     * valid provider is NOT partially published.
     */
    static final class InvalidContributionPlugin implements PylorosPlugin {
        @Override
        public String getPluginId() {
            return "invalid-contribution";
        }

        @Override
        public List<ToolProvider> createToolProviders(PluginContext context) {
            List<ToolProvider> contribution = new ArrayList<>();
            contribution.add(new NoopProvider("would-be-valid"));
            contribution.add(new NoopProvider("   "));
            return contribution;
        }
    }

    private static final class NoopProvider implements ToolProvider {
        private final String providerId;

        NoopProvider(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(List.of());
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            return Future.succeededFuture(Map.of("isError", false));
        }
    }

}
