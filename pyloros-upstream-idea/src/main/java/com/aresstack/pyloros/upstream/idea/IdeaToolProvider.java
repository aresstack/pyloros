package com.aresstack.pyloros.upstream.idea;

import com.aresstack.pyloros.tool.ToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
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

    public IdeaToolProvider(IdeaMcpConfig config) {
        this(config, null);
    }

    public IdeaToolProvider(IdeaMcpConfig config, IdeaMcpClient client) {
        this.config = config == null ? new IdeaMcpConfig(false, "127.0.0.1", 64343, "/sse", 3000, 60000, "idea__") : config;
        this.client = client;
    }

    @Override
    public String providerId() {
        return "intellij";
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
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode argumentsNode) {
        if (client == null || !client.isReady()) {
            return Future.succeededFuture(errorResult("IDEA MCP provider is not connected yet."));
        }

        String requestedName = upstreamToolName == null ? "" : upstreamToolName;

        JsonObject arguments;
        try {
            arguments = new JsonObject(argumentsNode == null ? "{}" : argumentsNode.toString());
        } catch (Exception ex) {
            log.debug("IdeaToolProvider: could not parse arguments for {}: {}", requestedName, ex.getMessage());
            arguments = new JsonObject();
        }

        JsonObject params = new JsonObject()
                .put("name", requestedName)
                .put("arguments", arguments);

        log.info("IdeaToolProvider: forwarding tools/call {}", requestedName);

        return client.call("tools/call", params)
                .map(response -> {
                    if (response == null) {
                        return errorResult("IDEA returned empty response for " + requestedName);
                    }
                    boolean ideaError = response.getBoolean("isError", false);
                    JsonArray content = response.getJsonArray("content");
                    if (content == null) {
                        String text = response.encode();
                        content = new JsonArray().add(new JsonObject().put("type", "text").put("text", text));
                    }
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
                            requestedName, contentList.size(), ideaError);
                    return Map.of("content", contentList, "isError", ideaError);
                })
                .recover(err -> {
                    log.debug("IdeaToolProvider: tools/call {} failed: {}", requestedName, err.getMessage());
                    String message = err.getMessage() == null ? "Unknown error" : err.getMessage();
                    return Future.succeededFuture(errorResult(message));
                });
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of(
                "content", new Object[]{Map.of("type", "text", "text", message)},
                "isError", true
        );
    }
}
