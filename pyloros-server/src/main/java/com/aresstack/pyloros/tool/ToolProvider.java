package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public interface ToolProvider {

    default String providerId() {
        return getClass().getName();
    }

    default String nativeToolName(String exposedToolName) {
        return exposedToolName;
    }

    Future<List<Map<String, Object>>> listTools();

    boolean supports(String toolName);

    Future<Map<String, Object>> callTool(McpToolCall toolCall);
}
