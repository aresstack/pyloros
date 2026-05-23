package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public final class PylorosPingToolProvider implements ToolProvider {

    public static final String TOOL_NAME = "pyloros__ping";
    public static final String SAVE_TOOL_NAME = new String(new char[]{112,121,108,111,114,111,115,95,95,115,107,105,108,108,115,95,115,97,118,101});

    @Override
    public String providerId() {
        return "pyloros";
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
        return Future.succeededFuture(List.of(toolDefinition(), saveToolDefinition()));
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        if (TOOL_NAME.equals(upstreamToolName)) {
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Pyloros Java gateway is alive.")),
                    "isError", false
            ));
        }

        if (SAVE_TOOL_NAME.equals(upstreamToolName)) {
            return Future.succeededFuture(saveUserContent(arguments));
        }

        return Future.succeededFuture(Map.of(
                "content", List.of(Map.of("type", "text", "text", "Tool not found: " + upstreamToolName)),
                "isError", true
        ));
    }

    private Map<String, Object> saveUserContent(JsonNode arguments) {
        String targetPlatform = textArgument(arguments, "targetPlatform");
        String itemId = textArgument(arguments, "skillId");
        String title = textArgument(arguments, "title");
        String description = textArgument(arguments, "description");
        String body = textArgument(arguments, "text");

        if (targetPlatform.isBlank()) {
            return error("Missing required argument: targetPlatform");
        }
        if (itemId.isBlank()) {
            return error("Missing required argument: skillId");
        }
        if (body.isBlank()) {
            return error("Missing required argument: text");
        }
        if (title.isBlank()) {
            title = itemId;
        }

        com.aresstack.pyloros.extension.UserDataFile file = new com.aresstack.pyloros.extension.UserDataFile();
        com.aresstack.pyloros.extension.UserContentEntry saved = file.save(targetPlatform, itemId, title, description, body);
        return success("Saved skill " + saved.getId() + " for target platform " + saved.getModuleId() + " at " + file.file());
    }

    private String textArgument(JsonNode arguments, String name) {
        if (arguments == null || !arguments.hasNonNull(name)) {
            return "";
        }
        return arguments.get(name).asText("").trim();
    }

    private Map<String, Object> success(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false);
    }

    private Map<String, Object> error(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", true);
    }

    private Map<String, Object> toolDefinition() {
        Map<String, Object> securityScheme = Map.of("type", "oauth2", "scopes", new String[]{"mcp"});

        return Map.of(
                "name", TOOL_NAME,
                "description", "Returns a small confirmation that Pyloros is alive.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                ),
                "securitySchemes", new Object[]{securityScheme},
                "_meta", Map.of("securitySchemes", new Object[]{securityScheme})
        );
    }

    private Map<String, Object> saveToolDefinition() {
        Map<String, Object> securityScheme = Map.of("type", "oauth2", "scopes", new String[]{"mcp"});

        return Map.of(
                "name", SAVE_TOOL_NAME,
                "description", "Saves or updates a user-provided target-platform skill in the Pyloros user directory.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "targetPlatform", Map.of("type", "string", "description", "Target platform id, for example intellij, copilot-cli, claude-cli, continue-cli, google-cli, or vscode."),
                                "skillId", Map.of("type", "string", "description", "Stable skill id."),
                                "title", Map.of("type", "string", "description", "Human-readable title."),
                                "description", Map.of("type", "string", "description", "Short summary."),
                                "text", Map.of("type", "string", "description", "Full skill text in Markdown or plain text.")
                        ),
                        "additionalProperties", false
                ),
                "securitySchemes", new Object[]{securityScheme},
                "_meta", Map.of("securitySchemes", new Object[]{securityScheme})
        );
    }
}
