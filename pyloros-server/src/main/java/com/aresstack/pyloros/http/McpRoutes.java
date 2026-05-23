package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.security.AuthenticationRequest;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.security.RequestAuthenticator;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolRouter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class McpRoutes {

    private static final Logger log = LoggerFactory.getLogger(McpRoutes.class);
    private static final String LEGACY_MCP_PUBLIC_PATH = "/sse";

    private final ServerConfig config;
    private final RequestAuthenticator authenticator;
    private final ToolCatalog toolCatalog;
    private final ToolRouter toolRouter;

    public McpRoutes(ServerConfig config, RequestAuthenticator authenticator, ToolCatalog toolCatalog, ToolRouter toolRouter) {
        this.config = config;
        this.authenticator = authenticator;
        this.toolCatalog = toolCatalog;
        this.toolRouter = toolRouter;
    }

    public void mount(Router router) {
        mountPath(router, config.mcpPublicPath());
        if (!LEGACY_MCP_PUBLIC_PATH.equals(config.mcpPublicPath())) {
            mountPath(router, LEGACY_MCP_PUBLIC_PATH);
        }
    }

    private void mountPath(Router router, String publicPath) {
        router.get(publicPath).handler(context -> {
            context.put("mcpBasePath", publicPath);
            mcpSse(context);
        });
        router.post(publicPath).handler(context -> {
            context.put("mcpBasePath", publicPath);
            mcpPost(context);
        });
        router.post(publicPath + "/*").handler(context -> {
            context.put("mcpBasePath", publicPath);
            mcpPost(context);
        });
    }

    private void mcpSse(RoutingContext context) {
        AuthenticationResult authentication = authenticator.authenticate(authenticationRequest(context));
        if (!authentication.isAuthenticated()) {
            unauthorized(context, authentication);
            return;
        }

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .putHeader(HttpHeaders.CONNECTION, "keep-alive");
        context.response().write("event: endpoint\n");
        context.response().write("data: " + mountedBasePath(context) + "\n\n");
    }

    private void mcpPost(RoutingContext context) {
        AuthenticationResult authentication = authenticator.authenticate(authenticationRequest(context));
        if (!authentication.isAuthenticated()) {
            unauthorized(context, authentication);
            return;
        }

        String pathToolName = ToolCallRequestResolver.resolvePathToolName(context.pathParam("*"));

        JsonNode request;
        try {
            request = HttpJson.mapper().readTree(context.body().asString());
        } catch (JsonProcessingException exception) {
            HttpJson.send(context, 400, Map.of("error", "Invalid JSON"));
            return;
        }

        if (ToolCallRequestResolver.isDirectPathInvocation(request, pathToolName)) {
            McpToolCall toolCall = ToolCallRequestResolver.resolvePathInvocationToolCall(request, pathToolName);
            logMcpPost(context, request, "path", toolCall.name());
            logToolsCall("path", toolCall);
            toolRouter.callTool(toolCall)
                    .onSuccess(result -> HttpJson.send(context, 200, result))
                    .onFailure(error -> HttpJson.send(context, 500, Map.of("error", error.getMessage())));
            return;
        }

        JsonNode id = request.get("id");
        String method = request.hasNonNull("method") ? request.get("method").asText() : null;
        boolean rpcToolCall = "tools/call".equals(method) || "call_tool".equals(method);
        if (!rpcToolCall) {
            logMcpPost(context, request, "rpc", null);
        }

        if (id == null || id.isNull()) {
            HttpJson.send(context, 202, Map.of("status", "accepted"));
            return;
        }

        switch (method == null ? "" : method) {
            case "initialize" -> initialize(context, id);
            case "tools/list" -> listTools(context, id);
            case "resources/list" -> HttpJson.rpcResult(context, id, Map.of("resources", new Object[]{}));
            case "prompts/list" -> HttpJson.rpcResult(context, id, Map.of("prompts", new Object[]{}));
            case "tools/call", "call_tool" -> callTool(context, id, request, pathToolName);
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
        toolCatalog.listTools()
                .onSuccess(tools -> HttpJson.rpcResult(context, id, Map.of("tools", tools)))
                .onFailure(error -> HttpJson.rpcError(context, id, -32000, error.getMessage()));
    }

    private void callTool(RoutingContext context, JsonNode id, JsonNode request, String fallbackToolName) {
        McpToolCall toolCall = ToolCallRequestResolver.resolveRpcToolCall(request, fallbackToolName);
        logMcpPost(context, request, "rpc", toolCall.name());
        logToolsCall("rpc", toolCall);
        toolRouter.callTool(toolCall)
                .onSuccess(result -> HttpJson.rpcResult(context, id, result))
                .onFailure(error -> HttpJson.rpcError(context, id, -32000, error.getMessage()));
    }

    private void logMcpPost(RoutingContext context, JsonNode request, String source, String resolvedToolName) {
        String method = request != null && request.hasNonNull("method") ? request.get("method").asText() : "";
        String toolName = resolvedToolName == null ? "" : resolvedToolName;
        log.info("[MCP] post path={} method={} source={} resolvedToolName={} deprecated={}",
                context.request().path(),
                method,
                source,
                toolName,
                isDeprecated(context));
    }

    private String mountedBasePath(RoutingContext context) {
        String mountedPath = context.get("mcpBasePath");
        return mountedPath == null || mountedPath.isBlank() ? config.mcpPublicPath() : mountedPath;
    }

    private boolean isDeprecated(RoutingContext context) {
        return LEGACY_MCP_PUBLIC_PATH.equals(mountedBasePath(context));
    }

    private void logToolsCall(String source, McpToolCall toolCall) {
        if (toolCall == null) {
            return;
        }
        log.info("[MCP] tools/call source={} name={} argumentKeys={}",
                source,
                toolCall.name(),
                argumentKeys(toolCall.arguments()));
    }

    private static List<String> argumentKeys(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        Iterator<String> fieldNames = arguments.fieldNames();
        while (fieldNames.hasNext()) {
            keys.add(fieldNames.next());
        }
        return keys;
    }


    private AuthenticationRequest authenticationRequest(RoutingContext context) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        context.request().headers().forEach(header -> headers.put(header.getKey(), header.getValue()));
        return new AuthenticationRequest(context.request().method().name(), context.request().path(), headers);
    }

    private void unauthorized(RoutingContext context, AuthenticationResult authentication) {
        log.info("[MCP] auth rejected reason={}", authentication.errorCode());
        try {
            context.response()
                    .setStatusCode(authentication.statusCode())
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader("Pragma", "no-cache");
            authentication.responseHeaders().forEach((name, value) -> context.response().putHeader(name, value));
            context.response().end("{\"error\":\"" + authentication.errorCode() + "\"}");
        } catch (Exception ex) {
            context.fail(ex);
        }
    }
}
