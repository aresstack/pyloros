package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.tool.ToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

    @Test
    void loadsSuccessfulPluginAndExposesContributedProvider() {
        WorkingPlugin instance = new WorkingPlugin();
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(WorkingPlugin.class, () -> instance)),
                Set.of()
        );

        PluginLoadResult result = registry.results().get(0);
        assertEquals("working", result.pluginId());
        assertEquals(PluginStatus.LOADED, result.status());
        assertNull(result.error());
        assertNotNull(result.descriptor());
        assertEquals(1, registry.contributedProviders().size());
        assertEquals("working-provider", registry.contributedProviders().get(0).providerId());
        assertEquals(1, registry.contributionResults().size());
        assertTrue(registry.contributionResults().get(0).accepted());
        assertSame(instance.initializeContext.get(), instance.contributeContext.get());
        assertEquals("working", instance.contributeContext.get().pluginId());
    }

    @Test
    void reportsDisabledPluginAsDisabledAndSkipsLifecycle() {
        WorkingPlugin instance = new WorkingPlugin();
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(WorkingPlugin.class, () -> instance)),
                Set.of("working")
        );

        PluginLoadResult result = registry.findById("working").orElseThrow();
        assertEquals(PluginStatus.DISABLED, result.status());
        assertNotNull(result.descriptor());
        assertTrue(registry.contributedProviders().isEmpty());
        assertFalse(instance.contributed);
        assertNull(instance.initializeContext.get());
    }

    @Test
    void reportsFailedToLoadWhenConstructorThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(ConstructorThrowingPlugin.class, () -> {
                    throw new IllegalStateException("ctor boom");
                })),
                Set.of()
        );

        PluginLoadResult result = registry.results().get(0);
        assertEquals(PluginStatus.FAILED_TO_LOAD, result.status());
        assertEquals(ConstructorThrowingPlugin.class.getName(), result.pluginId());
        assertNull(result.descriptor());
        assertNotNull(result.error());
        assertEquals(IllegalStateException.class.getName(), result.error().errorClass());
        assertTrue(registry.contributedProviders().isEmpty());
    }

    @Test
    void reportsFailedToLoadWhenDescriptorThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(DescriptorThrowingPlugin.class, DescriptorThrowingPlugin::new)),
                Set.of()
        );

        PluginLoadResult result = registry.results().get(0);
        assertEquals(PluginStatus.FAILED_TO_LOAD, result.status());
        assertEquals(DescriptorThrowingPlugin.class.getName(), result.pluginId());
        assertTrue(result.error().message().contains("descriptor boom"));
    }

    @Test
    void reportsFailedToInitializeWhenInitializeThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(InitializeThrowingPlugin.class, InitializeThrowingPlugin::new)),
                Set.of()
        );

        PluginLoadResult result = registry.results().get(0);
        assertEquals("initialize-throws", result.pluginId());
        assertEquals(PluginStatus.FAILED_TO_INITIALIZE, result.status());
        assertTrue(result.error().message().contains("initialize boom"));
        assertTrue(registry.contributedProviders().isEmpty());
    }

    @Test
    void reportsFailedToContributeAndDropsPartialProvidersWhenContributionInvalid() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(InvalidContributionPlugin.class, InvalidContributionPlugin::new)),
                Set.of()
        );

        PluginLoadResult result = registry.results().get(0);
        assertEquals("invalid-contribution", result.pluginId());
        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, result.status());
        assertNotNull(result.error());
        assertTrue(registry.contributedProviders().isEmpty());
        assertEquals(1, registry.contributionResults().size());
        assertFalse(registry.contributionResults().get(0).accepted());
    }

    @Test
    void reportsFailedToContributeWhenContributeThrows() {
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(ContributeThrowingPlugin.class, ContributeThrowingPlugin::new)),
                Set.of()
        );

        PluginLoadResult result = registry.results().get(0);
        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, result.status());
        assertTrue(result.error().message().contains("contribute boom"));
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
                Set.of()
        );

        assertEquals(3, registry.results().size());
        assertEquals(PluginStatus.FAILED_TO_LOAD, registry.results().get(0).status());
        assertEquals(PluginStatus.LOADED, registry.results().get(1).status());
        assertEquals(PluginStatus.FAILED_TO_CONTRIBUTE, registry.results().get(2).status());
        assertEquals(List.of("working-provider"),
                registry.contributedProviders().stream().map(ToolProvider::providerId).toList());
    }

    @Test
    void truncatesVeryLongErrorMessages() {
        String veryLong = "x".repeat(PluginErrorInfo.MAX_MESSAGE_LENGTH * 3);
        PluginErrorInfo info = PluginErrorInfo.from(new RuntimeException(veryLong));

        assertEquals(PluginErrorInfo.MAX_MESSAGE_LENGTH, info.message().length());
        assertTrue(info.message().endsWith("..."));
    }

    @Test
    void doesNotTruncateShortMessages() {
        PluginErrorInfo info = PluginErrorInfo.from(new RuntimeException("short"));
        assertEquals("short", info.message());
    }

    private static <P extends PylorosPlugin> ServiceLoader.Provider<P> candidate(
            Class<P> type, Supplier<P> factory) {
        return new ServiceLoader.Provider<>() {
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
        boolean contributed;
        final AtomicReference<PluginContext> initializeContext = new AtomicReference<>();
        final AtomicReference<PluginContext> contributeContext = new AtomicReference<>();

        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("working", "Working plugin", "1.0.0");
        }

        @Override
        public void initialize(PluginContext context) {
            initializeContext.set(context);
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            contributed = true;
            contributeContext.set(context);
            return PluginContribution.ofToolProviders(new NoopProvider("working-provider"));
        }
    }

    static final class ConstructorThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("ctor-throws");
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            return PluginContribution.empty();
        }
    }

    static final class DescriptorThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            throw new RuntimeException("descriptor boom");
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            return PluginContribution.empty();
        }
    }

    static final class InitializeThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("initialize-throws");
        }

        @Override
        public void initialize(PluginContext context) {
            throw new RuntimeException("initialize boom");
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            return PluginContribution.empty();
        }
    }

    static final class ContributeThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("contribute-throws");
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            throw new RuntimeException("contribute boom");
        }
    }

    static final class InvalidContributionPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("invalid-contribution");
        }

        @Override
        public PluginContribution contribute(PluginContext context) {
            return PluginContribution.ofToolProviders(
                    new NoopProvider("would-be-valid"),
                    new NoopProvider("   ")
            );
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
