package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public final class ToolRouter {

    private final ProviderRegistry providerRegistry;
    private final ToolCatalog toolCatalog;

    public ToolRouter(ProviderRegistry providerRegistry, ToolCatalog toolCatalog) {
        this.providerRegistry = providerRegistry;
        this.toolCatalog = toolCatalog;
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        if (toolCall == null || toolCall.name() == null || toolCall.name().isBlank()) {
            return toolNotFound("null");
        }

        ToolCatalogEntry entry = toolCatalog.findByExternalName(toolCall.name()).orElse(null);
        if (entry == null) {
            return toolNotFound(toolCall.name());
        }

        ToolAddress address = entry.address();
        return providerRegistry.findById(address.providerId())
                .map(provider -> provider.callTool(address.upstreamToolName(), toolCall.arguments()))
                .orElseGet(() -> toolNotFound(toolCall.name()));
    }

    private static Future<Map<String, Object>> toolNotFound(String toolName) {
        return Future.succeededFuture(Map.of(
                "content", List.of(Map.of("type", "text", "text", "Tool not found: " + toolName)),
                "isError", true
        ));
    }
}
