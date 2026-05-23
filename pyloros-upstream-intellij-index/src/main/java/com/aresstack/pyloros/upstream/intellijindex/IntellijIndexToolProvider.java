package com.aresstack.pyloros.upstream.intellijindex;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.upstream.mcp.GenericMcpToolProvider;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamClient;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamClients;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.List;
import java.util.Map;

public final class IntellijIndexToolProvider implements ToolProvider {

    private final GenericMcpToolProvider delegate;

    public IntellijIndexToolProvider(Vertx vertx, McpUpstreamConfig config) {
        McpUpstreamClient client = McpUpstreamClients.create(vertx, config);
        this.delegate = new GenericMcpToolProvider(config, client);
    }

    @Override
    public String providerId() {
        return delegate.providerId();
    }

    @Override
    public String nativeToolName(String exposedToolName) {
        return delegate.nativeToolName(exposedToolName);
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return delegate.listTools();
    }

    @Override
    public boolean supports(String toolName) {
        return delegate.supports(toolName);
    }

    @Override
    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        return delegate.callTool(toolCall);
    }
}
