package com.aresstack.pyloros.http;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class ToolCallRequestResolver {

    private ToolCallRequestResolver() {
    }

    static String resolvePathToolName(String wildcardPathParam) {
        if (wildcardPathParam == null || wildcardPathParam.isBlank()) {
            return "";
        }
        String decoded = URLDecoder.decode(wildcardPathParam, StandardCharsets.UTF_8);
        return decoded.startsWith("/") ? decoded.substring(1) : decoded;
    }

    static boolean isDirectPathInvocation(JsonNode request, String pathToolName) {
        if (pathToolName == null || pathToolName.isBlank()) {
            return false;
        }
        return request == null || !request.hasNonNull("method");
    }

    static McpToolCall resolvePathInvocationToolCall(JsonNode request, String pathToolName) {
        JsonNode arguments;
        if (request == null || request.isNull()) {
            arguments = HttpJson.mapper().createObjectNode();
        } else if (request.has("arguments")) {
            arguments = request.get("arguments");
        } else if (request.has("input")) {
            arguments = request.get("input");
        } else {
            arguments = request;
        }

        if (arguments == null || arguments.isNull()) {
            arguments = HttpJson.mapper().createObjectNode();
        }
        return new McpToolCall(pathToolName == null ? "" : pathToolName, arguments);
    }

    static McpToolCall resolveRpcToolCall(JsonNode request, String fallbackToolName) {
        JsonNode params = request != null && request.hasNonNull("params")
                ? request.get("params")
                : HttpJson.mapper().createObjectNode();

        String name = firstNonBlank(
                text(params, "name"),
                text(params, "tool"),
                text(params, "toolName"),
                fallbackToolName
        );

        JsonNode arguments = params.has("arguments") ? params.get("arguments") : params.get("input");
        if (arguments == null || arguments.isNull()) {
            arguments = HttpJson.mapper().createObjectNode();
        }
        return new McpToolCall(name, arguments);
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

