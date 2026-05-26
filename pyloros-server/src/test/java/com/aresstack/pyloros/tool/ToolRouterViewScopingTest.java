package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.provider.ProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRouterViewScopingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void callToolRespectsRequestedToolView() {
        ToolProvider provider = new PublicOnlyProvider();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry);
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        String externalToolName = (String) await(toolCatalog.listTools(ToolView.PUBLIC)).getFirst().get("name");

        Map<String, Object> publicResult = await(toolRouter.callTool(new McpToolCall(externalToolName, JSON.createObjectNode()), ToolView.PUBLIC));
        assertFalse((Boolean) publicResult.getOrDefault("isError", false));

        Map<String, Object> agentResult = await(toolRouter.callTool(new McpToolCall(externalToolName, JSON.createObjectNode()), ToolView.AGENT));
        assertTrue((Boolean) agentResult.get("isError"));
        assertEquals("Tool not found: " + externalToolName,
                ((Map<?, ?>) ((List<?>) agentResult.get("content")).getFirst()).get("text"));
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static final class PublicOnlyProvider implements ToolProvider {
        @Override
        public String providerId() {
            return "public-provider";
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
            return Future.succeededFuture(List.of(Map.of(
                    "name", "status",
                    "description", "public status",
                    "inputSchema", Map.of("type", "object")
            )));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, com.fasterxml.jackson.databind.JsonNode arguments) {
            ObjectNode payload = JSON.createObjectNode().put("ok", true);
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", payload.toString())),
                    "isError", false
            ));
        }
    }
}
