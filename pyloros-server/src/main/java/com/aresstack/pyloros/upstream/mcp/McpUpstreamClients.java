package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Vertx;

public final class McpUpstreamClients {

    private McpUpstreamClients() {
    }

    public static McpUpstreamClient create(Vertx vertx, McpUpstreamConfig config) {
        if (config == null) {
            return new NoopMcpUpstreamClient();
        }

        String transport = config.transport().trim().toLowerCase();
        return switch (transport) {
            case "streamable-http" -> new StreamableHttpMcpUpstreamClient(vertx, config);
            case "sse" -> new SseMcpUpstreamClient(vertx, config);
            default -> new NoopMcpUpstreamClient();
        };
    }
}
