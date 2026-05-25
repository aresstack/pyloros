package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.tool.ToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

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
                Set.of()
        );

        assertEquals(1, registry.results().size());
        PluginLoadResult result = registry.results().get(0);
        assertEquals("working", result.pluginId());
        assertEquals(PluginStatus.LOADED, result.status());
        assertNull(result.error());
        assertNotNull(result.descriptor());
        assertEquals("working", result.descriptor().id());

        assertEquals(1, registry.contributedProviders().size());
        assertEquals("working-provider", registry.contributedProviders().get(0).providerId());

        assertEquals(1, registry.contributionResults().size());
        assertTrue(registry.contributionResults().get(0).accepted());
    }

    @Test
    void reportsDisabledPluginAsDisabledAndSkipsContribution() {
        WorkingPlugin instance = new WorkingPlugin();
        PluginRegistry registry = PluginRegistry.loadFrom(
                List.of(candidate(WorkingPlugin.class, () -> instance)),
                Set.of("working")
        );

        PluginLoadResult result = registry.findById("working").orElseThrow();
        assertEquals(PluginStatus.DISABLED, result.status());
        assertNotNull(result.descriptor(), "descriptor must be available for disabled plugins");
        assertTrue(registry.contributedProviders().isEmpty(), "disabled plugin must not contribute");
        assertFalse(instance.contributed, "disabled plugin must not be asked for providers");
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
        assertNull(result.descriptor(), "no canonical descriptor when load failed");
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
        assertEquals(DescriptorThrowingPlugin.class.getName(), result.pluginId(),
                "must fall back to impl class name when descriptor() throws");
        assertTrue(result.error().message().contains("descriptor boom"));
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
        assertTrue(registry.contributedProviders().isEmpty(),
                "partial contribution from a failing plugin must not be published");

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
        boolean contributed;

        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("working", "Working plugin", "1.0.0");
        }

        @Override
        public PluginContribution contribute() {
            contributed = true;
            return PluginContribution.ofToolProviders(new NoopProvider("working-provider"));
        }
    }

    static final class ConstructorThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("ctor-throws");
        }

        @Override
        public PluginContribution contribute() {
            return PluginContribution.empty();
        }
    }

    static final class DescriptorThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            throw new RuntimeException("descriptor boom");
        }

        @Override
        public PluginContribution contribute() {
            return PluginContribution.empty();
        }
    }

    static final class ContributeThrowingPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("contribute-throws");
        }

        @Override
        public PluginContribution contribute() {
            throw new RuntimeException("contribute boom");
        }
    }

    /**
     * Returns a contribution that contains one valid provider and one provider
     * with a blank providerId. The whole contribution must be rejected so that
     * the valid provider is NOT partially published.
     */
    static final class InvalidContributionPlugin implements PylorosPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return PluginDescriptor.of("invalid-contribution");
        }

        @Override
        public PluginContribution contribute() {
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
