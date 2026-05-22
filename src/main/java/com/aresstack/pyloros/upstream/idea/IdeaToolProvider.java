package com.aresstack.pyloros.upstream.idea;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.domain.tool.ToolProvider;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public final class IdeaToolProvider implements ToolProvider {

    private final IdeaMcpConfig config;

    public IdeaToolProvider(IdeaMcpConfig config) {
        this.config = config == null ? new IdeaMcpConfig(false, "127.0.0.1", 64343, "/sse", 3000, 60000, "idea__") : config;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        // 001-A: no connection yet; if disabled return empty list, otherwise also empty for now
        if (!config.enabled()) {
            return Future.succeededFuture(List.of());
        }
        return Future.succeededFuture(List.of());
    }

    @Override
    public boolean supports(String toolName) {
        if (!config.enabled() || toolName == null) {
            return false;
        }
        return toolName.startsWith(config.toolPrefix());
    }

    @Override
    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        // 001-A: provider not connected yet
        return Future.succeededFuture(Map.of(
                "content", new Object[]{Map.of("type", "text", "text", "IDEA MCP provider is configured but not connected yet.")},
                "isError", true
        ));
    }
}

