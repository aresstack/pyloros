package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.oauth.OAuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public final class McpRoutes {

    private final PylorosConfig config;
    private final OAuthService oauthService;

    public McpRoutes(PylorosConfig config, OAuthService oauthService) {
        this.config = config;
        this.oauthService = oauthService;
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
            case "initialize" -> HttpJson.rpcResult(context, id, Map.of(
                    "protocolVersion", config.mcpProtocolVersion(),
                    "capabilities", Map.of("tools", Map.of(), "resources", Map.of(), "prompts", Map.of()),
                    "serverInfo", Map.of("name", "pyloros", "version", "0.1.0")
            ));
            case "tools/list" -> HttpJson.rpcResult(context, id, Map.of("tools", new Object[]{dummyTool()}));
            case "resources/list" -> HttpJson.rpcResult(context, id, Map.of("resources", new Object[]{}));
            case "prompts/list" -> HttpJson.rpcResult(context, id, Map.of("prompts", new Object[]{}));
            case "tools/call", "call_tool" -> HttpJson.rpcResult(context, id, Map.of(
                    "content", new Object[]{Map.of("type", "text", "text", "Pyloros Java gateway is alive.")},
                    "isError", false
            ));
            default -> HttpJson.rpcError(context, id, -32601, "Method not supported");
        }
    }

    private Map<String, Object> dummyTool() {
        return Map.of(
                "name", "pyloros__ping",
                "description", "Returns a small confirmation that Pyloros is alive.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                ),
                "securitySchemes", new Object[]{Map.of("type", "oauth2", "scopes", new String[]{"mcp"})},
                "_meta", Map.of("securitySchemes", new Object[]{Map.of("type", "oauth2", "scopes", new String[]{"mcp"})})
        );
    }

    private boolean isAuthorized(RoutingContext context) {
        return oauthService.isBearerAuthorized(context.request().getHeader(HttpHeaders.AUTHORIZATION));
    }

    private void unauthorized(RoutingContext context) {
        context.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Bearer realm=\"pyloros\", resource_metadata=\"" + config.publicOrigin() + "/.well-known/oauth-protected-resource\"")
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .end("{\"error\":\"Unauthorized\"}");
    }
}
