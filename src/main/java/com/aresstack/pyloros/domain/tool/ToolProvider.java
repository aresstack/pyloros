package com.aresstack.pyloros.domain.tool;

import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public interface ToolProvider {

    Future<List<Map<String, Object>>> listTools();

    boolean supports(String toolName);

    Future<Map<String, Object>> callTool(McpToolCall toolCall);
}
