package com.aresstack.pyloros.example.plugin;

import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ExampleEchoToolProvider implements ToolProvider {

    static final String PROVIDER_ID = "example-tools";
    static final String TOOL_NAME = "echo";

    private final String pluginId;

    ExampleEchoToolProvider(String pluginId) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId must not be null");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.MCP;
    }

    @Override
    public List<ToolView> exposedViews() {
        return List.of(ToolView.PUBLIC);
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return Future.succeededFuture(List.of(Map.of(
                "name", TOOL_NAME,
                "description", "Echoes the input text back to the caller",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of(
                                        "type", "string",
                                        "description", "Text to echo back"
                                )
                        ),
                        "required", List.of("text")
                )
        )));
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        if (!TOOL_NAME.equals(upstreamToolName)) {
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Unknown tool: " + upstreamToolName)),
                    "isError", true
            ));
        }

        JsonNode textNode = arguments == null ? null : arguments.get("text");
        String text = (textNode == null || textNode.isNull()) ? "" : textNode.asText("");
        return Future.succeededFuture(Map.of(
                "content", List.of(Map.of("type", "text", "text", text)),
                "isError", false,
                "pluginId", pluginId
        ));
    }
}
