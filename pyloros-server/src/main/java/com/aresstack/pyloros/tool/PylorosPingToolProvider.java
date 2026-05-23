package com.aresstack.pyloros.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public final class PylorosPingToolProvider implements ToolProvider {

    public static final String TOOL_NAME = "pyloros__ping";

    @Override
    public String providerId() {
        return "pyloros";
    }

    @Override
    public boolean preservesUpstreamToolName() {
        return true;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return Future.succeededFuture(List.of(toolDefinition()));
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        if (!TOOL_NAME.equals(upstreamToolName)) {
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Tool not found: " + upstreamToolName)),
                    "isError", true
            ));
        }

        return Future.succeededFuture(Map.of(
                "content", List.of(Map.of("type", "text", "text", "Pyloros Java gateway is alive.")),
                "isError", false
        ));
    }

    private Map<String, Object> toolDefinition() {
        Map<String, Object> securityScheme = Map.of("type", "oauth2", "scopes", new String[]{"mcp"});

        return Map.of(
                "name", TOOL_NAME,
                "description", "Returns a small confirmation that Pyloros is alive.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                ),
                "securitySchemes", new Object[]{securityScheme},
                "_meta", Map.of("securitySchemes", new Object[]{securityScheme})
        );
    }
}
