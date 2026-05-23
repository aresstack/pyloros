package com.aresstack.pyloros.upstream.idea;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.tool.ToolProvider;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IdeaToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(IdeaToolProvider.class);

    private final IdeaMcpConfig config;
    private final IdeaMcpClient client;
    private final IdeaToolNameMapper toolNameMapper;

    public IdeaToolProvider(IdeaMcpConfig config) {
        this(config, null);
    }

    public IdeaToolProvider(IdeaMcpConfig config, IdeaMcpClient client) {
        this.config = config == null ? new IdeaMcpConfig(false, "127.0.0.1", 64343, "/sse", 3000, 60000, "idea__") : config;
        this.client = client;
        this.toolNameMapper = new IdeaToolNameMapper(this.config.toolPrefix());
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

        // Always claim namespaced IDEA aliases so outage handling happens in callTool()
        // with a controlled provider error instead of ToolRegistry unsupported fallback.
        if (toolNameMapper.isNamespacedAlias(toolName)) {
            return true;
        }

        if (client == null || !client.isReady()) {
            return false;
        }

        // Unprefixed compatibility alias:
        // - optimistic until known tools are loaded (to avoid first-call unsupported errors)
        // - strict membership check once known tools are available
        if (!client.hasKnownTools()) {
            return true;
        }
        return client.isKnownOriginalTool(toolName);
    }

    @Override
    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        if (client == null || !client.isReady()) {
            return Future.succeededFuture(errorResult("IDEA MCP provider is not connected yet."));
        }

        String requestedName = toolCall.name() == null ? "" : toolCall.name();

        return resolveOriginalName(requestedName)
                .compose(originalName -> {
                    // Convert Jackson JsonNode arguments to Vert.x JsonObject
                    JsonObject arguments;
                    try {
                        String argStr = toolCall.arguments() == null ? "{}" : toolCall.arguments().toString();
                        arguments = new JsonObject(argStr);
                    } catch (Exception ex) {
                        log.debug("IdeaToolProvider: could not parse arguments for {}: {}", originalName, ex.getMessage());
                        arguments = new JsonObject();
                    }

                    JsonObject params = new JsonObject()
                            .put("name", originalName)
                            .put("arguments", arguments);

                    log.info("IdeaToolProvider: forwarding tools/call {} -> {}", requestedName, originalName);

                    return client.call("tools/call", params)
                            .map(response -> {
                                if (response == null) {
                                    return errorResult("IDEA returned empty response for " + originalName);
                                }
                                // If IDEA returned isError=true, forward it
                                boolean ideaError = response.getBoolean("isError", false);
                                JsonArray content = response.getJsonArray("content");
                                if (content == null) {
                                    // No content array — wrap the entire response as a text result
                                    String text = response.encode();
                                    content = new JsonArray().add(new JsonObject().put("type", "text").put("text", text));
                                }
                                // Convert JsonArray to List<Map<String,Object>>
                                List<Object> contentList = new ArrayList<>();
                                for (int i = 0; i < content.size(); i++) {
                                    Object item = content.getValue(i);
                                    if (item instanceof JsonObject jo) {
                                        contentList.add(jo.getMap());
                                    } else {
                                        contentList.add(item);
                                    }
                                }
                                log.info("IdeaToolProvider: tools/call {} returned {} content items (isError={})",
                                        originalName, contentList.size(), ideaError);
                                return Map.of("content", contentList, "isError", ideaError);
                            });
                })
                .recover(err -> {
                    log.debug("IdeaToolProvider: tools/call {} failed: {}", requestedName, err.getMessage());
                    String message = err.getMessage() == null ? "Unknown error" : err.getMessage();
                    return Future.succeededFuture(errorResult(message));
                });
    }

    private Future<String> resolveOriginalName(String requestedName) {
        String originalName = toolNameMapper.toOriginalName(requestedName);

        if (toolNameMapper.isNamespacedAlias(requestedName)) {
            return Future.succeededFuture(originalName);
        }

        if (client.hasKnownTools()) {
            if (client.isKnownOriginalTool(originalName)) {
                return Future.succeededFuture(originalName);
            }
            return Future.failedFuture(new IllegalArgumentException("Unsupported tool: " + requestedName));
        }

        // Warm-up path for old cached unprefixed names: fetch current tool list once,
        // then validate strictly against known upstream tools.
        return client.listTools().compose(ignored -> {
            if (client.isKnownOriginalTool(originalName)) {
                return Future.succeededFuture(originalName);
            }
            return Future.failedFuture(new IllegalArgumentException("Unsupported tool: " + requestedName));
        });
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of(
                "content", new Object[]{Map.of("type", "text", "text", message)},
                "isError", true
        );
    }
}



