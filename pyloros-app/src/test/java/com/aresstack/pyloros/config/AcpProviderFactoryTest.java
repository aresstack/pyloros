package com.aresstack.pyloros.config;

import com.aresstack.pyloros.tool.ToolProvider;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpProviderFactoryTest {

    @Test
    void rejectsManagerProviderWhenAgentViewIncludesOtherAcpProviderExposure() {
        Vertx vertx = Vertx.vertx();
        try {
            AcpProviderJsonConfig managerConfig = acpConfig("manager", "manager-agent-view", List.of("public"));
            AcpProviderJsonConfig otherConfig = acpConfig("other-acp", "other-agent-view", List.of("manager-agent-view"));

            List<ToolProvider> providers = AcpProviderFactory.createProviders(List.of(managerConfig, otherConfig), vertx);

            assertEquals(1, providers.size());
            assertEquals("other-acp", providers.getFirst().providerId());
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Test
    void allowsManagerProviderWhenNoOtherAcpProviderIsExposedInAgentView() {
        Vertx vertx = Vertx.vertx();
        try {
            AcpProviderJsonConfig managerConfig = acpConfig("manager", "manager-agent-view", List.of("public"));
            AcpProviderJsonConfig otherConfig = acpConfig("other-acp", "other-agent-view", List.of("public"));

            List<ToolProvider> providers = AcpProviderFactory.createProviders(List.of(managerConfig, otherConfig), vertx);

            assertEquals(2, providers.size());
            assertTrue(providers.stream().anyMatch(provider -> "manager".equals(provider.providerId())));
            assertTrue(providers.stream().anyMatch(provider -> "other-acp".equals(provider.providerId())));
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Test
    void ignoresInvalidAcpConfigWhenBuildingViewCollisionMap() {
        Vertx vertx = Vertx.vertx();
        try {
            AcpProviderJsonConfig managerConfig = acpConfig("manager", "manager-agent-view", List.of("public"));
            AcpProviderJsonConfig invalidConfig = new AcpProviderJsonConfig(
                    "broken",
                    "acp",
                    "broken/",
                    List.of("manager-agent-view"),
                    "other-agent-view",
                    new AcpProviderJsonConfig.AcpProcessJsonConfig("   ", List.of(), null, Map.of()),
                    null
            );

            List<ToolProvider> providers = AcpProviderFactory.createProviders(List.of(managerConfig, invalidConfig), vertx);

            assertEquals(1, providers.size());
            assertEquals("manager", providers.getFirst().providerId());
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    private static AcpProviderJsonConfig acpConfig(String id, String agentToolView, List<String> exposeInViews) {
        return new AcpProviderJsonConfig(
                id,
                "acp",
                id + "/",
                exposeInViews,
                agentToolView,
                new AcpProviderJsonConfig.AcpProcessJsonConfig("fake-acp", List.of(), null, Map.of()),
                null
        );
    }
}
