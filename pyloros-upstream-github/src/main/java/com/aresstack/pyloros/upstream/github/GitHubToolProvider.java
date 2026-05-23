package com.aresstack.pyloros.upstream.github;

import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.upstream.mcp.GenericMcpToolProvider;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamClient;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamClients;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.List;
import java.util.Map;

public final class GitHubToolProvider implements ToolProvider {

    private final GenericMcpToolProvider delegate;

    public GitHubToolProvider(Vertx vertx, McpUpstreamConfig config) {
        McpUpstreamClient client = McpUpstreamClients.create(vertx, config);
        this.delegate = new GenericMcpToolProvider(config, client);
    }

    @Override
    public String providerId() {
        return delegate.providerId();
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return delegate.listTools();
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        return delegate.callTool(upstreamToolName, arguments);
    }
}
