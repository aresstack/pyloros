package com.aresstack.pyloros.intellij;

import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public final class IntellijMcpSetupToolProvider implements ToolProvider {

    public static final String RECOMMENDED_SETUP_TOOL = "pyloros__intellij_recommended_setup";
    public static final String CONFIG_TEMPLATE_TOOL = "pyloros__intellij_mcp_config_template";

    @Override
    public String providerId() {
        return "pyloros-intellij-mcp";
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.NATIVE;
    }

    @Override
    public boolean preservesUpstreamToolName() {
        return true;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return Future.succeededFuture(List.of(recommendedSetupToolDefinition(), configTemplateToolDefinition()));
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        if (RECOMMENDED_SETUP_TOOL.equals(upstreamToolName)) {
            return Future.succeededFuture(success(IntellijMcpTexts.RECOMMENDED_SETUP));
        }
        if (CONFIG_TEMPLATE_TOOL.equals(upstreamToolName)) {
            return Future.succeededFuture(success(IntellijMcpTexts.CONFIG_TEMPLATE));
        }
        return Future.succeededFuture(error("Tool not found: " + upstreamToolName));
    }

    private Map<String, Object> recommendedSetupToolDefinition() {
        return toolDefinition(RECOMMENDED_SETUP_TOOL, "Explains the recommended IntelliJ MCP plugin setup for Pyloros agents.", Map.of());
    }

    private Map<String, Object> configTemplateToolDefinition() {
        return toolDefinition(CONFIG_TEMPLATE_TOOL, "Returns a safe MCP config template for IntelliJ, MCP Steroid, IDE Index MCP Server, and GitHub MCP Server.", Map.of());
    }

    private Map<String, Object> toolDefinition(String name, String description, Map<String, Object> properties) {
        Map<String, Object> securityScheme = Map.of("type", "oauth2", "scopes", new String[]{"mcp"});
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", properties,
                        "additionalProperties", false
                ),
                "securitySchemes", new Object[]{securityScheme},
                "_meta", Map.of("securitySchemes", new Object[]{securityScheme})
        );
    }

    private Map<String, Object> success(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false);
    }

    private Map<String, Object> error(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", true);
    }
}
