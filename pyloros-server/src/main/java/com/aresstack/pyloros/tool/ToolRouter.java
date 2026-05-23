package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import io.vertx.core.Future;

import java.util.Map;
import java.util.Optional;

public final class ToolRouter {

    private final ProviderRegistry providerRegistry;
    private final ToolCatalog toolCatalog;

    public ToolRouter(ProviderRegistry providerRegistry, ToolCatalog toolCatalog) {
        this.providerRegistry = providerRegistry;
        this.toolCatalog = toolCatalog;
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        if (toolCall == null || toolCall.name() == null) {
            return unsupportedTool("null");
        }

        Optional<ToolAddress> resolved = toolCatalog.resolve(toolCall.name());
        if (resolved.isPresent()) {
            ToolAddress address = resolved.get();
            return providerRegistry.findById(address.providerId())
                    .map(provider -> provider.callTool(new McpToolCall(address.nativeToolName(), toolCall.arguments())))
                    .orElseGet(() -> unsupportedTool(toolCall.name()));
        }

        // Transitional compatibility path for aliases that are callable but not listed in tools/list.
        for (ToolProvider provider : providerRegistry.providers()) {
            if (provider.supports(toolCall.name())) {
                return provider.callTool(toolCall);
            }
        }

        return unsupportedTool(toolCall.name());
    }

    private static Future<Map<String, Object>> unsupportedTool(String toolName) {
        return Future.succeededFuture(Map.of(
                "content", new Object[]{Map.of("type", "text", "text", "Unsupported tool: " + toolName)},
                "isError", true
        ));
    }
}

