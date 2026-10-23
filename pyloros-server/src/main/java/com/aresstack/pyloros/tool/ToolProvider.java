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

    /**
     * Scope a caller must hold for this tool, or {@code null} when the gate's scope
     * is enough.
     *
     * <p>Only the provider knows whether a tool reads or writes, so the mapping
     * belongs here rather than in the central configuration. Returning {@code null}
     * by default keeps every existing provider working unchanged.
     */
    default String requiredScope(String upstreamToolName) {
        return null;
    }

    Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments);
}
    