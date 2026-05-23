package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public interface ToolProvider {

    default String providerId() {
        return getClass().getName();
    }

    default ProviderType providerType() {
        return ProviderType.UNKNOWN;
    }

    default List<ToolView> exposedViews() {
        return List.of(ToolView.PUBLIC);
    }

    default boolean preservesUpstreamToolName() {
        return false;
    }

    Future<List<Map<String, Object>>> listTools();

    default Future<List<Map<String, Object>>> listTools(ToolView toolView) {
        return listTools();
    }

    Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments);
}
    