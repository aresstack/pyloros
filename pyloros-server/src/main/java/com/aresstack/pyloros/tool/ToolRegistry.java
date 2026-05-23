package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

/**
 * Transitional compatibility wrapper. New code should prefer ProviderRegistry + ToolCatalog + ToolRouter directly.
 */
@Deprecated(forRemoval = false)
public final class ToolRegistry {

    private final ProviderRegistry providerRegistry;
    private final ToolCatalog toolCatalog;
    private final ToolRouter toolRouter;

    public ToolRegistry(List<ToolProvider> providers) {
        this.providerRegistry = new ProviderRegistry(providers);
        this.toolCatalog = new ToolCatalog(providerRegistry);
        this.toolRouter = new ToolRouter(providerRegistry, toolCatalog);
    }

    public Future<List<Map<String, Object>>> listTools() {
        return toolCatalog.listTools();
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        return toolRouter.callTool(toolCall);
    }
}
