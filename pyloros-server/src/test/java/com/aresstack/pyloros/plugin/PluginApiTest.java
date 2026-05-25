package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginApiTest {

    /**
     * Acceptance: a plugin can supply a unique id and metadata such as name,
     * version and description.
     */
    @Test
    void pluginDescriptorExposesIdNameVersionAndDescription() {
        PluginDescriptor descriptor = new PluginDescriptor(
                "com.example.foo",
                "Foo Plugin",
                "1.2.3",
                "Adds Foo tools to Pyloros.");

        assertEquals("com.example.foo", descriptor.id());
        assertEquals("Foo Plugin", descriptor.name());
        assertEquals("1.2.3", descriptor.version());
        assertEquals("Adds Foo tools to Pyloros.", descriptor.description());
        assertTrue(descriptor.optionalName().isPresent());
        assertTrue(descriptor.optionalVersion().isPresent());
        assertTrue(descriptor.optionalDescription().isPresent());
    }

    /**
     * Acceptance: a missing (null or blank) plugin id is validated at the API
     * boundary.
     */
    @Test
    void pluginDescriptorRejectsMissingId() {
        assertThrows(NullPointerException.class,
                () -> new PluginDescriptor(null, "", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new PluginDescriptor("", "", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new PluginDescriptor("   ", "", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> PluginDescriptor.of(""));
    }

    /**
     * Acceptance: a plugin can contribute a {@link ToolProvider}, and an
     * example implementation compiles against the plugin API.
     */
    @Test
    void pluginCanContributeAToolProvider() {
        PylorosPlugin plugin = new SamplePlugin();

        PluginDescriptor descriptor = plugin.descriptor();
        PluginContribution contribution = plugin.contribute();

        assertEquals("com.example.sample", descriptor.id());
        assertFalse(contribution.isEmpty());
        assertEquals(1, contribution.toolProviders().size());
        assertEquals("sample", contribution.toolProviders().get(0).providerId());
    }

    @Test
    void emptyContributionIsShared() {
        assertSame(PluginContribution.empty(), PluginContribution.empty());
        assertTrue(PluginContribution.empty().isEmpty());
        assertTrue(PluginContribution.empty().toolProviders().isEmpty());
    }

    @Test
    void contributionDefensivelyCopiesAndRejectsNullEntries() {
        SampleToolProvider provider = new SampleToolProvider();
        List<ToolProvider> mutable = new java.util.ArrayList<>();
        mutable.add(provider);
        PluginContribution contribution = new PluginContribution(mutable);

        mutable.clear();
        assertEquals(1, contribution.toolProviders().size());
        assertThrows(UnsupportedOperationException.class,
                () -> contribution.toolProviders().add(provider));

        assertThrows(NullPointerException.class,
                () -> new PluginContribution(java.util.Arrays.asList(provider, null)));
        assertThrows(NullPointerException.class,
                () -> new PluginContribution(null));
    }

    /**
     * Acceptance: duplicate plugin ids must be reportable through the plugin
     * API. {@link PluginContributionResult} carries the per-plugin outcome so
     * that a host can later expose duplicate-id rejections.
     */
    @Test
    void pluginContributionResultCanReportDuplicateIdRejection() {
        PluginDescriptor descriptor = PluginDescriptor.of("com.example.dup", "Dup", "1.0.0");
        PluginContribution contribution = PluginContribution.empty();

        PluginContributionResult accepted = PluginContributionResult.accepted(descriptor, contribution);
        assertTrue(accepted.accepted());
        assertEquals("", accepted.rejectionReason());
        assertTrue(accepted.optionalRejectionReason().isEmpty());

        PluginContributionResult rejected = PluginContributionResult.rejected(
                descriptor, contribution, "duplicate plugin id 'com.example.dup'");
        assertFalse(rejected.accepted());
        assertEquals("duplicate plugin id 'com.example.dup'", rejected.rejectionReason());
        assertEquals("duplicate plugin id 'com.example.dup'",
                rejected.optionalRejectionReason().orElseThrow());
        assertSame(descriptor, rejected.descriptor());
        assertSame(contribution, rejected.contribution());
    }

    @Test
    void pluginContributionResultEnforcesConsistencyBetweenFlagAndReason() {
        PluginDescriptor descriptor = PluginDescriptor.of("com.example.x");
        PluginContribution contribution = PluginContribution.empty();

        assertThrows(IllegalArgumentException.class, () -> PluginContributionResult.rejected(
                descriptor, contribution, "   "));
        assertThrows(NullPointerException.class, () -> PluginContributionResult.rejected(
                descriptor, contribution, null));
        assertThrows(IllegalArgumentException.class, () -> new PluginContributionResult(
                descriptor, contribution, true, "should be blank"));
        assertThrows(IllegalArgumentException.class, () -> new PluginContributionResult(
                descriptor, contribution, false, ""));
        assertThrows(NullPointerException.class, () -> new PluginContributionResult(
                null, contribution, true, ""));
        assertThrows(NullPointerException.class, () -> new PluginContributionResult(
                descriptor, null, true, ""));
    }

    /**
     * Simulates how a host could detect duplicate plugin ids while collecting
     * contributions from several plugins. This exercises only the public API
     * surface defined by this issue.
     */
    @Test
    void hostCanDetectDuplicatePluginIdsUsingTheApi() {
        List<PylorosPlugin> plugins = List.of(
                new SamplePlugin(),
                new SamplePlugin()); // same id as the first plugin

        Map<String, PluginDescriptor> seen = new LinkedHashMap<>();
        List<PluginContributionResult> results = new java.util.ArrayList<>();
        for (PylorosPlugin plugin : plugins) {
            PluginDescriptor descriptor = plugin.descriptor();
            PluginContribution contribution = plugin.contribute();
            if (seen.containsKey(descriptor.id())) {
                results.add(PluginContributionResult.rejected(
                        descriptor, contribution,
                        "duplicate plugin id '" + descriptor.id() + "'"));
            } else {
                seen.put(descriptor.id(), descriptor);
                results.add(PluginContributionResult.accepted(descriptor, contribution));
            }
        }

        assertEquals(2, results.size());
        assertTrue(results.get(0).accepted());
        assertFalse(results.get(1).accepted());
        assertEquals("duplicate plugin id 'com.example.sample'",
                results.get(1).rejectionReason());
    }

    /**
     * Example plugin implementation used in the tests above. Its mere
     * existence proves that an example implementation compiles against the
     * plugin API.
     */
    private static final class SamplePlugin implements PylorosPlugin {

        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor(
                    "com.example.sample",
                    "Sample Plugin",
                    "0.1.0",
                    "Example plugin for tests.");
        }

        @Override
        public PluginContribution contribute() {
            return PluginContribution.ofToolProviders(new SampleToolProvider());
        }
    }

    private static final class SampleToolProvider implements ToolProvider {

        @Override
        public String providerId() {
            return "sample";
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.NATIVE;
        }

        @Override
        public List<ToolView> exposedViews() {
            return List.of(ToolView.PUBLIC);
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            Map<String, Object> tool = new HashMap<>();
            tool.put("name", "ping");
            return Future.succeededFuture(List.of(tool));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            assertNotNull(upstreamToolName);
            return Future.succeededFuture(Map.of("ok", true));
        }
    }
}
