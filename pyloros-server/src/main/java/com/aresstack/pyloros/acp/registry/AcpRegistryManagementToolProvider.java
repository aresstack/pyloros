package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Exposes ACP registry management tools:
 * <ul>
 *   <li>{@code install_agent} – installs a registry agent</li>
 *   <li>{@code uninstall_agent} – uninstalls a registry agent</li>
 *   <li>{@code enable_agent} – enables an installed agent</li>
 *   <li>{@code disable_agent} – disables an installed agent</li>
 *   <li>{@code get_agent_status} – gets status of an installed agent</li>
 *   <li>{@code list_agents} – lists all installed agents</li>
 *   <li>{@code configure_agent} – updates agent configuration</li>
 * </ul>
 */
public final class AcpRegistryManagementToolProvider implements ToolProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROVIDER_ID = "acp_registry";

    private final AgentLifecycleService lifecycleService;

    public AcpRegistryManagementToolProvider(AgentLifecycleService lifecycleService) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.NATIVE;
    }

    @Override
    public List<ToolView> exposedViews() {
        return List.of(ToolView.PUBLIC);
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(toolDef("install_agent",
                "Install an ACP registry agent. Requires: id, command. Optional: args, prefix, exposeInViews, agentToolView, environment, enabled.",
                requiredSchema("id", "command")));
        tools.add(toolDef("uninstall_agent",
                "Uninstall an ACP registry agent by id.",
                requiredSchema("id")));
        tools.add(toolDef("enable_agent",
                "Enable a previously disabled ACP registry agent.",
                requiredSchema("id")));
        tools.add(toolDef("disable_agent",
                "Disable an installed ACP registry agent (removes its tools without uninstalling).",
                requiredSchema("id")));
        tools.add(toolDef("get_agent_status",
                "Get the current status and configuration of an installed ACP registry agent.",
                requiredSchema("id")));
        tools.add(toolDef("list_agents",
                "List all installed ACP registry agents.",
                Map.of("type", "object")));
        tools.add(toolDef("configure_agent",
                "Update configuration of an installed agent (command, args, prefix, exposeInViews, agentToolView). Refreshes provider dynamically.",
                requiredSchema("id")));
        return Future.succeededFuture(tools);
    }

    @Override
    public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
        try {
            return Future.succeededFuture(dispatch(upstreamToolName, arguments));
        } catch (Exception e) {
            return Future.succeededFuture(errorResult(e.getMessage()));
        }
    }

    private Map<String, Object> dispatch(String toolName, JsonNode args) {
        return switch (toolName) {
            case "install_agent" -> handleInstall(args);
            case "uninstall_agent" -> handleUninstall(args);
            case "enable_agent" -> handleEnable(args);
            case "disable_agent" -> handleDisable(args);
            case "get_agent_status" -> handleGetStatus(args);
            case "list_agents" -> handleListAgents();
            case "configure_agent" -> handleConfigure(args);
            default -> errorResult("Unknown tool: " + toolName);
        };
    }

    private Map<String, Object> handleInstall(JsonNode args) {
        String id = requireText(args, "id");
        String command = requireText(args, "command");
        List<String> argsList = textList(args, "args");
        String prefix = optionalText(args, "prefix");
        List<String> exposeInViews = textList(args, "exposeInViews");
        String agentToolView = optionalText(args, "agentToolView");
        Map<String, String> environment = textMap(args, "environment");
        boolean enabled = args.has("enabled") ? args.get("enabled").asBoolean(true) : true;

        InstalledAgent agent = new InstalledAgent(id, enabled, command, argsList, prefix, exposeInViews, agentToolView, environment);
        lifecycleService.install(agent);
        return successResult("Agent '" + id + "' installed successfully.", agent);
    }

    private Map<String, Object> handleUninstall(JsonNode args) {
        String id = requireText(args, "id");
        boolean removed = lifecycleService.uninstall(id);
        if (removed) {
            return successResult("Agent '" + id + "' uninstalled successfully.", null);
        }
        return errorResult("Agent '" + id + "' not found.");
    }

    private Map<String, Object> handleEnable(JsonNode args) {
        String id = requireText(args, "id");
        Optional<InstalledAgent> result = lifecycleService.enable(id);
        return result.map(agent -> successResult("Agent '" + id + "' enabled.", agent))
                .orElseGet(() -> errorResult("Agent '" + id + "' not found."));
    }

    private Map<String, Object> handleDisable(JsonNode args) {
        String id = requireText(args, "id");
        Optional<InstalledAgent> result = lifecycleService.disable(id);
        return result.map(agent -> successResult("Agent '" + id + "' disabled.", agent))
                .orElseGet(() -> errorResult("Agent '" + id + "' not found."));
    }

    private Map<String, Object> handleGetStatus(JsonNode args) {
        String id = requireText(args, "id");
        Optional<InstalledAgent> result = lifecycleService.getStatus(id);
        return result.map(agent -> successResult("Agent '" + id + "' status.", agent))
                .orElseGet(() -> errorResult("Agent '" + id + "' not found."));
    }

    private Map<String, Object> handleListAgents() {
        List<InstalledAgent> agents = lifecycleService.listAll();
        ObjectNode data = MAPPER.createObjectNode();
        data.set("agents", MAPPER.valueToTree(agents));
        return Map.of("isError", false, "content", List.of(Map.of("type", "text", "text", data.toString())));
    }

    private Map<String, Object> handleConfigure(JsonNode args) {
        String id = requireText(args, "id");
        Optional<InstalledAgent> existing = lifecycleService.getStatus(id);
        if (existing.isEmpty()) {
            return errorResult("Agent '" + id + "' not found.");
        }

        InstalledAgent current = existing.get();
        InstalledAgent updated = new InstalledAgent(
                id,
                args.has("enabled") ? args.get("enabled").asBoolean() : current.enabled(),
                args.has("command") ? args.get("command").asText() : current.command(),
                args.has("args") ? textList(args, "args") : current.args(),
                args.has("prefix") ? args.get("prefix").asText() : current.prefix(),
                args.has("exposeInViews") ? textList(args, "exposeInViews") : current.exposeInViews(),
                args.has("agentToolView") ? args.get("agentToolView").asText() : current.agentToolView(),
                args.has("environment") ? textMap(args, "environment") : current.environment()
        );

        Optional<InstalledAgent> result = lifecycleService.configure(id, updated);
        return result.map(agent -> successResult("Agent '" + id + "' reconfigured.", agent))
                .orElseGet(() -> errorResult("Agent '" + id + "' not found."));
    }

    private static Map<String, Object> successResult(String message, InstalledAgent agent) {
        ObjectNode content = MAPPER.createObjectNode();
        content.put("message", message);
        if (agent != null) {
            content.set("agent", MAPPER.valueToTree(agent));
        }
        return Map.of("isError", false, "content", List.of(Map.of("type", "text", "text", content.toString())));
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", message)));
    }

    private static Map<String, Object> toolDef(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        return tool;
    }

    private static Map<String, Object> requiredSchema(String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of(required));
        return schema;
    }

    private static String requireText(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        String value = args.get(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Field must not be blank: " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) {
            return null;
        }
        return args.get(field).asText();
    }

    private static List<String> textList(JsonNode args, String field) {
        if (args == null || !args.has(field) || !args.get(field).isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode element : args.get(field)) {
            result.add(element.asText());
        }
        return result;
    }

    private static Map<String, String> textMap(JsonNode args, String field) {
        if (args == null || !args.has(field) || !args.get(field).isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        args.get(field).fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }
}
