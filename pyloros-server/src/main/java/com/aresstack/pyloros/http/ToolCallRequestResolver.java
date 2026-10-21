package com.aresstack.pyloros.http;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.fasterxml.jackson.databind.JsonNode;

final class ToolCallRequestResolver {

    private ToolCallRequestResolver() {
    }

    static McpToolCall resolveRpcToolCall(JsonNode request) {
        JsonNode params = request != null && request.hasNonNull("params")
                ? request.get("params")
                : HttpJson.mapper().createObjectNode();

        String toolName = firstNonBlank(
                text(params, "name"),
                text(params, "tool"),
                text(params, "toolName")
        );

        JsonNode arguments = params.has("arguments") ? params.get("arguments") : params.get("input");
        if (arguments == null || arguments.isNull()) {
            arguments = HttpJson.mapper().createObjectNode();
        }
        return new McpToolCall(toolName, arguments);
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
