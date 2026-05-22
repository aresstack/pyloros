package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.domain.tool.ToolProvider;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {

    private final List<ToolProvider> providers;

    public ToolRegistry(List<ToolProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Future<List<Map<String, Object>>> listTools() {
        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (ToolProvider provider : providers) {
            futures.add(provider.listTools());
        }

        return Future.all(new ArrayList<>(futures)).map(composite -> {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (int index = 0; index < futures.size(); index++) {
                tools.addAll(composite.resultAt(index));
            }
            return tools;
        });
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        for (ToolProvider provider : providers) {
            if (provider.supports(toolCall.name())) {
                return provider.callTool(toolCall);
            }
        }

        return Future.succeededFuture(Map.of(
                "content", new Object[]{Map.of("type", "text", "text", "Unsupported tool: " + toolCall.name())},
                "isError", true
        ));
    }
}
