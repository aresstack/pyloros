package com.aresstack.pyloros.upstream.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenericMcpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(GenericMcpToolProvider.class);

    private final McpUpstreamConfig config;
    private final McpUpstreamClient client;

    public GenericMcpToolProvider(McpUpstreamConfig config, McpUpstreamClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public String providerId() {
        return config.providerId();
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return client.listTools().map(tools -> {
            List<Map<String, Object>> upstreamTools = new ArrayList<>();
            for (Map<String, Object> item : tools) {
                if (item == null) {
                    continue;
                }
                upstreamTools.add(new LinkedHashMap<>(item));
            }
            return upstreamTools;
        }).recover(err -> {
            log.warn("[MCP-UPSTREAM] provider={} unavailable reason={}", config.providerId(), err.getMessage());
            return Future.succeededFuture(List.of());
        });
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode argumentsNode) {
        String requested = upstreamToolName == null ? "" : upstreamToolName;

        JsonObject arguments;
        try {
            arguments = new JsonObject(argumentsNode == null ? "{}" : argumentsNode.toString());
        } catch (Exception ex) {
            arguments = new JsonObject();
        }

        log.info("[MCP-UPSTREAM] provider={} tools/call {}", config.providerId(), requested);

        return client.callTool(requested, arguments)
                .map(this::normalizeCallResult)
                .recover(err -> {
                    log.warn("[MCP-UPSTREAM] provider={} tools/call {} failed: {}", config.providerId(), requested, err.getMessage());
                    return Future.succeededFuture(errorResult(err.getMessage()));
                });
    }

    private Map<String, Object> normalizeCallResult(JsonObject result) {
        if (result == null) {
            return errorResult("Upstream returned empty response");
        }

        boolean isError = result.getBoolean("isError", false);
        JsonArray content = result.getJsonArray("content");
        if (content == null) {
            content = new JsonArray().add(new JsonObject().put("type", "text").put("text", result.encode()));
        }

        List<Object> contentList = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            Object item = content.getValue(i);
            if (item instanceof JsonObject jsonObject) {
                contentList.add(jsonObject.getMap());
            } else {
                contentList.add(item);
            }
        }

        return Map.of("content", contentList, "isError", isError);
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of(
                "content", new Object[]{Map.of("type", "text", "text", message == null ? "Unknown error" : message)},
                "isError", true
        );
    }
}
