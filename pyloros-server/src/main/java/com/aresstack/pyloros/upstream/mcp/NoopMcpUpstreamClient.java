package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

final class NoopMcpUpstreamClient implements McpUpstreamClient {
    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return Future.succeededFuture(List.of());
    }

    @Override
    public Future<JsonObject> callTool(String nativeToolName, JsonObject arguments) {
        return Future.failedFuture("Upstream disabled or unsupported transport");
    }
}
