package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.oauth.OAuthService;
import com.aresstack.pyloros.tool.ToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public final class McpRoutes {

    private final PylorosConfig config;
    private final OAuthService oauthService;
    private final ToolRegistry toolRegistry;

    public McpRoutes(PylorosConfig config, OAuthService oauthService, ToolRegistry toolRegistry) {
        this.config = config;
        this.oauthService = oauthService;
        this.toolRegistry = toolRegistry;
    }

    public void mount(Router router) {
        router.get(config.mcpPublicPath()).handler(this::mcpSse);
        router.post(config.mcpPublicPath()).handler(this::mcpPost);
    }

    private void mcpSse(RoutingContext context) {
        if (!isAuthorized(context)) {
            unauthorized(context);
            return;
        }

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .putHeader(HttpHeaders.CONNECTION, "keep-alive");
        context.response().write("event: endpoint\n");
        context.response().write("data: " + config.mcpPublicPath() + "\n\n");
    }

    private void mcpPost(RoutingContext context) {
        if (!isAuthorized(context)) {
            unauthorized(context);
            return;
        }

        JsonNode request;
        try {
            request = HttpJson.mapper().readTree(context.body().asString());
        } catch (JsonProcessingException exception) {
            HttpJson.send(context, 400, Map.of("error", "Invalid JSON"));
            return;
        }

        JsonNode id = request.get("id");
        String method = request.hasNonNull("method") ? request.get("method").asText() : null;

        if (id == null || id.isNull()) {
            HttpJson.send(context, 202, Map.of("status", "accepted"));
            return;
        }

        switch (method == null ? "" : method) {
            case "initialize" -> initialize(context, id);
            case "tools/list" -> listTools(context, id);
            case "resources/list" -> HttpJson.rpcResult(context, id, Map.of("resources", new Object[]{}));
            case "prompts/list" -> HttpJson.rpcResult(context, id, Map.of("prompts", new Object[]{}));
            case "tools/call", "call_tool" -> callTool(context, id, request);
            default -> HttpJson.rpcError(context, id, -32601, "Method not supported");
        }
    }

    private void initialize(RoutingContext context, JsonNode id) {
        HttpJson.rpcResult(context, id, Map.of(
                "protocolVersion", config.mcpProtocolVersion(),
                "capabilities", Map.of("tools", Map.of(), "resources", Map.of(), "prompts", Map.of()),
                "serverInfo", Map.of("name", "pyloros", "version", "0.1.0")
        ));
    }

    private void listTools(RoutingContext context, JsonNode id) {
        toolRegistry.listTools()
                .onSuccess(tools -> HttpJson.rpcResult(context, id, Map.of("tools", tools)))
                .onFailure(error -> HttpJson.rpcError(context, id, -32000, error.getMessage()));
    }

    private void callTool(RoutingContext context, JsonNode id, JsonNode request) {
        McpToolCall toolCall = extractToolCall(request);
        toolRegistry.callTool(toolCall)
                .onSuccess(result -> HttpJson.rpcResult(context, id, result))
                .onFailure(error -> HttpJson.rpcError(context, id, -32000, error.getMessage()));
    }

    private McpToolCall extractToolCall(JsonNode request) {
        JsonNode params = request.hasNonNull("params") ? request.get("params") : HttpJson.mapper().createObjectNode();
        String name = params.hasNonNull("name") ? params.get("name").asText() : "";
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : params.get("input");
        if (arguments == null || arguments.isNull()) {
            arguments = HttpJson.mapper().createObjectNode();
        }
        return new McpToolCall(name, arguments);
    }

    private boolean isAuthorized(RoutingContext context) {
        return oauthService.isBearerAuthorized(context.request().getHeader(HttpHeaders.AUTHORIZATION));
    }

    private void unauthorized(RoutingContext context) {
        context.response()
                .setStatusCode(401)
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .end("{\"error\":\"Unauthorized\"}");
    }
}
