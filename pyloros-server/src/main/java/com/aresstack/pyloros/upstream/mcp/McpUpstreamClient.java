package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Map;

public interface McpUpstreamClient {

    default Future<Void> start() {
        return Future.succeededFuture();
    }

    Future<List<Map<String, Object>>> listTools();

    Future<JsonObject> callTool(String nativeToolName, JsonObject arguments);

    default Future<Void> stop() {
        return Future.succeededFuture();
    }
}
