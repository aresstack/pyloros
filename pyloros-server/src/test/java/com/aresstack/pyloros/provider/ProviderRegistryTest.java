package com.aresstack.pyloros.provider;

import com.aresstack.pyloros.tool.PylorosPingToolProvider;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {

    @Test
    void exposesProviderDescriptorsInRegistrationOrder() {
        ProviderRegistry registry = new ProviderRegistry(List.of(
                new PylorosPingToolProvider(),
                new MetadataProvider("github", ProviderType.MCP, List.of(ToolView.PUBLIC, ToolView.AGENT))
        ));

        List<ProviderDescriptor> descriptors = registry.descriptors();

        assertEquals(2, descriptors.size());
        assertEquals("pyloros", descriptors.get(0).providerId());
        assertEquals(ProviderType.NATIVE, descriptors.get(0).providerType());
        assertEquals("github", descriptors.get(1).providerId());
        assertEquals(ProviderType.MCP, descriptors.get(1).providerType());
        assertTrue(descriptors.get(1).isExposedIn(ToolView.PUBLIC));
        assertTrue(descriptors.get(1).isExposedIn(ToolView.AGENT));
        assertFalse(descriptors.get(1).isExposedIn(ToolView.LLM_AGENT));
    }

    @Test
    void reportsDefaultProviderStatusAsAvailable() {
        ProviderRegistry registry = new ProviderRegistry(List.of(new PylorosPingToolProvider()));

        assertEquals(ProviderStatus.AVAILABLE, registry.status("pyloros"));
        assertEquals(ProviderStatus.UNKNOWN, registry.status("missing"));
    }

    @Test
    void findsDescriptorByProviderId() {
        ProviderRegistry registry = new ProviderRegistry(List.of(new PylorosPingToolProvider()));

        ProviderDescriptor descriptor = registry.findDescriptorById("pyloros").orElseThrow(AssertionError::new);

        assertEquals("pyloros", descriptor.providerId());
        assertEquals(ProviderType.NATIVE, descriptor.providerType());
        assertTrue(descriptor.preservesUpstreamToolName());
    }

    private static final class MetadataProvider implements ToolProvider {

        private final String providerId;
        private final ProviderType providerType;
        private final List<ToolView> exposedViews;

        private MetadataProvider(String providerId, ProviderType providerType, List<ToolView> exposedViews) {
            this.providerId = providerId;
            this.providerType = providerType;
            this.exposedViews = exposedViews;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public ProviderType providerType() {
            return providerType;
        }

        @Override
        public List<ToolView> exposedViews() {
            return exposedViews;
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
    