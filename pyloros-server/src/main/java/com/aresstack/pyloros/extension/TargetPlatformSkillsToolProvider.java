package com.aresstack.pyloros.extension;
    
    import com.aresstack.pyloros.provider.ProviderType;
    import com.aresstack.pyloros.tool.ToolProvider;
    import com.fasterxml.jackson.databind.JsonNode;
    import io.vertx.core.Future;
    
    import java.util.LinkedHashMap;
    import java.util.List;
    import java.util.Map;
    import java.util.Optional;
    import java.util.stream.Collectors;
    
    public final class TargetPlatformSkillsToolProvider implements ToolProvider {
    
        public static final String LIST_SKILLS_TOOL = "pyloros__skills_list";
        public static final String GET_SKILL_TOOL = "pyloros__skills_get";
    
        private final LoadedTargetPlatformModules modules;
    
        public TargetPlatformSkillsToolProvider(LoadedTargetPlatformModules modules) {
            this.modules = modules;
        }
    
        @Override
        public String providerId() {
            return "pyloros-skills";
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
            return Future.succeededFuture(List.of(listSkillsToolDefinition(), getSkillToolDefinition()));
        }
    
        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            if (LIST_SKILLS_TOOL.equals(upstreamToolName)) {
                return Future.succeededFuture(success(listSkills()));
            }
            if (GET_SKILL_TOOL.equals(upstreamToolName)) {
                String skillId = arguments == null || !arguments.hasNonNull("skillId")
                        ? ""
                        : arguments.get("skillId").asText();
                return Future.succeededFuture(getSkill(skillId));
            }
            return Future.succeededFuture(error("Tool not found: " + upstreamToolName));
        }
    
        private String listSkills() {
            List<TargetPlatformSkill> skills = allSkills();
            if (skills.isEmpty()) {
                return "No target-platform skills are available. Enable a target platform module such as intellij.";
            }
    
            return skills.stream()
                    .map(skill -> skill.id() + " — " + skill.title() + " (module: " + skill.moduleId() + ")\n" + skill.description())
                    .collect(Collectors.joining("\n\n"));
        }
    
        private Map<String, Object> getSkill(String skillId) {
            if (skillId == null || skillId.isBlank()) {
                return error("Missing required argument: skillId");
            }
    
            Optional<TargetPlatformSkill> skill = allSkills().stream()
                    .filter(candidate -> skillId.equals(candidate.id()))
                    .findFirst();
    
            return skill.map(value -> success(value.text()))
                    .orElseGet(() -> error("Skill not found: " + skillId));
        }
    
        private List<TargetPlatformSkill> allSkills() {
        List<TargetPlatformSkill> skills = new java.util.ArrayList<>();
        skills.addAll(new UserDataFile().loadTargetPlatformSkills());
        skills.addAll(modules.skills());
        return List.copyOf(skills);
    }

    private Map<String, Object> listSkillsToolDefinition() {
            return toolDefinition(
                    LIST_SKILLS_TOOL,
                    "Lists skills provided by enabled Pyloros target platform modules.",
                    Map.of()
            );
        }
    
        private Map<String, Object> getSkillToolDefinition() {
            return toolDefinition(
                    GET_SKILL_TOOL,
                    "Returns the full text of a skill provided by an enabled Pyloros target platform module.",
                    Map.of(
                            "skillId", Map.of(
                                    "type", "string",
                                    "description", "Skill id returned by pyloros__skills_list."
                            )
                    )
            );
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
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", text)),
                    "isError", false
            );
        }
    
        private Map<String, Object> error(String text) {
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", text)),
                    "isError", true
            );
        }
    }
    
