package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {

    private final List<ToolProvider> providers;

    public ToolRegistry(List<ToolProvider> providers) {
        // Defensively copy providers; accept null as empty list so callers don't need to check
        this.providers = List.copyOf(providers == null ? List.of() : providers);
    }

    public Future<List<Map<String, Object>>> listTools() {
        // If there are no providers, return an empty tool list immediately
        if (providers.isEmpty()) {
            return Future.succeededFuture(List.of());
        }

        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (ToolProvider provider : providers) {
            futures.add(provider.listTools());
        }

        return Future.all(new ArrayList<>(futures)).map(composite -> {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (int index = 0; index < futures.size(); index++) {
                List<Map<String, Object>> result = composite.resultAt(index);
                if (result != null) {
                    tools.addAll(result);
                }
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
