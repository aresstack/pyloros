package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.extension.UserDataFile;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class UserSkillDeleteToolProvider implements ToolProvider {
    public static final String TOOL_NAME = new String(new char[]{112,121,108,111,114,111,115,95,95,115,107,105,108,108,115,95,100,101,108,101,116,101});

    @Override
    public boolean preservesUpstreamToolName() {
        return true;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        return Future.succeededFuture(List.of(toolDefinition()));
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        if (!TOOL_NAME.equals(upstreamToolName)) {
            return Future.failedFuture("Unknown tool: " + upstreamToolName);
        }
        try {
            return Future.succeededFuture(removeUserSkill(arguments));
        } catch (RuntimeException exception) {
            return Future.failedFuture(exception);
        }
    }

    private Map<String, Object> removeUserSkill(JsonNode arguments) {
        String targetPlatform = text(arguments, "targetPlatform");
        String skillId = text(arguments, "skillId");
        if (targetPlatform == null || targetPlatform.isBlank()) {
            throw new IllegalArgumentException("targetPlatform is required");
        }
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId is required");
        }

        UserDataFile userDataFile = new UserDataFile();
        Path path = userDataFile.file();
        boolean removed = UserSkillJson.remove(path, targetPlatform, skillId);
        String message = removed
                ? "Removed skill " + skillId + " for target platform " + targetPlatform + " at " + path
                : "No stored skill " + skillId + " for target platform " + targetPlatform + " found at " + path;
        return textResult(message);
    }

    private String text(JsonNode arguments, String fieldName) {
        if (arguments == null || !arguments.has(fieldName) || arguments.get(fieldName).isNull()) {
            return null;
        }
        return arguments.get(fieldName).asText();
    }

    private Map<String, Object> textResult(String text) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)));
    }

    private Map<String, Object> toolDefinition() {
        return Map.of(
                "name", TOOL_NAME,
                "description", "Removes a user-stored target-platform skill from the Pyloros user directory.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "targetPlatform", Map.of("type", "string", "description", "Target platform id."),
                                "skillId", Map.of("type", "string", "description", "Stable skill id.")
                        ),
                        "required", List.of("targetPlatform", "skillId")
                )
        );
    }
}
