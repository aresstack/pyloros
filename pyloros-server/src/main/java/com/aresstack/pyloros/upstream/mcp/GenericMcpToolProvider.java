package com.aresstack.pyloros.upstream.mcp;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.tool.ToolProvider;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public String nativeToolName(String exposedToolName) {
        String prefix = config.normalizedPrefix();
        if (exposedToolName != null && exposedToolName.startsWith(prefix)) {
            return exposedToolName.substring(prefix.length());
        }
        return exposedToolName;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        if (!config.isEnabled()) {
            return Future.succeededFuture(List.of());
        }
        if (config.requiresToken() && !config.hasToken()) {
            log.info("[MCP-UPSTREAM] provider={} unavailable reason=token-not-configured", config.providerId());
            return Future.succeededFuture(List.of());
        }

        return client.listTools().map(tools -> {
            List<Map<String, Object>> exposed = new ArrayList<>();
            String prefix = config.normalizedPrefix();
            for (Map<String, Object> item : tools) {
                if (item == null) {
                    continue;
                }
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>(item);
                Object nameObj = copy.get("name");
                if (nameObj instanceof String name && !name.isBlank()) {
                    copy.put("name", prefix + name);
                }
                exposed.add(copy);
            }
            return exposed;
        }).recover(err -> {
            log.warn("[MCP-UPSTREAM] provider={} unavailable reason={}", config.providerId(), err.getMessage());
            return Future.succeededFuture(List.of());
        });
    }

    @Override
    public boolean supports(String toolName) {
        return config.isEnabled()
                && (!config.requiresToken() || config.hasToken())
                && toolName != null
                && toolName.startsWith(config.normalizedPrefix());
    }

    @Override
    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        if (!config.isEnabled()) {
            return Future.succeededFuture(errorResult("Provider is disabled: " + config.providerId()));
        }
        if (config.requiresToken() && !config.hasToken()) {
            return Future.succeededFuture(errorResult("Provider token not configured: " + config.providerId()));
        }

        String requested = toolCall.name() == null ? "" : toolCall.name();
        String nativeName = nativeToolName(requested);

        JsonObject arguments;
        try {
            arguments = new JsonObject(toolCall.arguments() == null ? "{}" : toolCall.arguments().toString());
        } catch (Exception ex) {
            arguments = new JsonObject();
        }

        log.info("[MCP-UPSTREAM] provider={} tools/call {} -> {}", config.providerId(), requested, nativeName);

        return client.callTool(nativeName, arguments)
                .map(this::normalizeCallResult)
                .recover(err -> {
                    log.warn("[MCP-UPSTREAM] provider={} tools/call {} failed: {}", config.providerId(), nativeName, err.getMessage());
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
