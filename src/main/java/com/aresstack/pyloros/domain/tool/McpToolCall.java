package com.aresstack.pyloros.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolCall(
        String name,
        JsonNode arguments
) {
}
