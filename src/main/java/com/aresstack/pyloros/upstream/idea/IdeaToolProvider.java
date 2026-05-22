package com.aresstack.pyloros.upstream.idea;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.domain.tool.ToolProvider;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;
import com.aresstack.pyloros.upstream.idea.IdeaMcpClient;

public final class IdeaToolProvider implements ToolProvider {

    private final IdeaMcpConfig config;
    private final IdeaMcpClient client;

    public IdeaToolProvider(IdeaMcpConfig config) {
        this(config, null);
    }

    public IdeaToolProvider(IdeaMcpConfig config, IdeaMcpClient client) {
        this.config = config == null ? new IdeaMcpConfig(false, "127.0.0.1", 64343, "/sse", 3000, 60000, "idea__") : config;
        this.client = client;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        if (!config.enabled()) {
            return Future.succeededFuture(List.of());
        }

        if (client == null) {
            return Future.succeededFuture(List.of());
        }

        try {
            return client.listTools();
        } catch (Exception ex) {
            return Future.succeededFuture(List.of());
        }
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
        // 001-B: if client not provided or not ready, return a clear not-connected error
        if (client == null || !client.isReady()) {
            return Future.succeededFuture(Map.of(
                    "content", new Object[]{Map.of("type", "text", "text", "IDEA MCP provider is not connected yet.")},
                    "isError", true
            ));
        }

        // For 001-B we still do not forward calls to IDEA; indicate not implemented yet
        return Future.succeededFuture(Map.of(
                "content", new Object[]{Map.of("type", "text", "text", "IDEA MCP provider call not implemented yet.")},
                "isError", true
        ));
    }
}

