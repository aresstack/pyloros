package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.security.AuthenticationRequest;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.security.RequestAuthenticator;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.net.SocketAddress;
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
    private static final String TOOL_VIEW_QUERY_PARAM = "view";
    private static final String TRUSTED_VIEW_SOURCE_HEADER = "X-Pyloros-Injected-View";
    private static final String TRUSTED_VIEW_SOURCE_VALUE = "acp";
    static final String TRUSTED_VIEW_TOKEN_HEADER = "X-Pyloros-Injected-View-Token";

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
        router.get("/").handler(this::mcpGet);
        router.post("/").handler(this::mcpPost);
    }

    /**
     * MCP Streamable HTTP allows a server that does not offer a server-initiated SSE stream to
     * answer GET on the MCP endpoint with {@code 405 Method Not Allowed}.
     *
     * <p>Pyloros has no server-to-client JSON-RPC messages to push: every response is produced
     * synchronously on the POST that requested it. Previously this handler emitted a single
     * {@code event: endpoint} frame — a leftover of the deprecated HTTP+SSE transport — and closed
     * the stream. A Streamable HTTP client that opens this stream receives a {@code text/event-stream}
     * response that never carries a JSON-RPC message and then ends, which it can only report as a
     * broken stream. Answering 405 tells the client unambiguously that there is no GET stream.
     */
    private void mcpGet(RoutingContext context) {
        AuthenticationResult authentication = authenticator.authenticate(authenticationRequest(context));
        if (!authentication.isAuthenticated()) {
            unauthorized(context, authentication);
            return;
        }

        context.response().putHeader(HttpHeaders.ALLOW, "POST");
        HttpJson.send(context, 405, Map.of("error", "GET is not supported on the MCP endpoint"));
    }

    private void mcpPost(RoutingContext context) {
        AuthenticationResult authentication = authenticator.authenticate(authenticationRequest(context));
        if (!authentication.isAuthenticated()) {
            unauthorized(context, authentication);
            return;
        }

        ToolView requestedToolView;
        try {
            requestedToolView = resolveRequestedToolView(context);
        } catch (IllegalArgumentException exception) {
            HttpJson.rpcError(context, null, -32602, HttpJson.errorMessage(exception));
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
        boolean rpcToolCall = "tools/call".equals(method) || "call_tool".equals(method);
        if (!rpcToolCall) {
            logMcpPost(context, request, "rpc", null);
        }

        if (id == null || id.isNull()) {
            // A JSON-RPC notification carries no id and has no response. MCP Streamable HTTP
            // requires a bodyless 202 Accepted here; a JSON body would be an unexpected payload
            // for a client that is not reading one.
            HttpJson.sendNoBody(context, 202);
            return;
        }

        switch (method == null ? "" : method) {
            case "initialize" -> initialize(context, id);
            case "tools/list" -> listTools(context, id, requestedToolView);
            case "resources/list" -> HttpJson.rpcResult(context, id, Map.of("resources", new Object[]{}));
            case "prompts/list" -> HttpJson.rpcResult(context, id, Map.of("prompts", new Object[]{}));
            case "tools/call", "call_tool" -> callTool(context, id, request, requestedToolView);
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

    private void listTools(RoutingContext context, JsonNode id, ToolView toolView) {
        toolCatalog.listTools(toolView)
                .onSuccess(tools -> HttpJson.rpcResult(context, id, Map.of("tools", tools == null ? List.of() : tools)))
                .onFailure(error -> HttpJson.rpcError(context, id, -32000, HttpJson.errorMessage(error)));
    }

    private void callTool(RoutingContext context, JsonNode id, JsonNode request, ToolView toolView) {
        McpToolCall toolCall = ToolCallRequestResolver.resolveRpcToolCall(request);
        logMcpPost(context, request, "rpc", toolCall.name());
        logToolsCall("rpc", toolCall);
        toolRouter.callTool(toolCall, toolView)
                .onSuccess(result -> HttpJson.rpcResult(context, id, result))
                .onFailure(error -> HttpJson.rpcError(context, id, -32000, HttpJson.errorMessage(error)));
    }

    private ToolView resolveRequestedToolView(RoutingContext context) {
        String requestedView = context.request().getParam(TOOL_VIEW_QUERY_PARAM);
        if (requestedView == null || requestedView.isBlank()) {
            return ToolView.PUBLIC;
        }
        if (!isTrustedInjectedViewRequest(context)) {
            return ToolView.PUBLIC;
        }
        return ToolView.named(requestedView.trim());
    }

    private boolean isTrustedInjectedViewRequest(RoutingContext context) {
        String injectedViewHeader = context.request().getHeader(TRUSTED_VIEW_SOURCE_HEADER);
        String injectedTokenHeader = context.request().getHeader(TRUSTED_VIEW_TOKEN_HEADER);
        SocketAddress remoteAddress = context.request().remoteAddress();
        String remoteHost = remoteAddress == null ? null : remoteAddress.hostAddress();
        return isTrustedInjectedViewRequest(
                injectedViewHeader,
                injectedTokenHeader,
                remoteHost,
                config.mcpInjectedViewToken());
    }

    /**
     * Package-private for testability.
     *
     * <p>Returns {@code true} only when ALL of the following hold:
     * <ul>
     *   <li>{@code configuredToken} is non-blank — non-public views are disabled when no
     *       shared secret has been configured/generated.</li>
     *   <li>{@code injectedViewHeader} equals {@code "acp"} (case-insensitive) — declarative
     *       marker that the caller intends to use the injected agent path.</li>
     *   <li>{@code injectedTokenHeader} equals {@code configuredToken} via constant-time
     *       comparison — proves the caller actually holds the secret Pyloros generated.</li>
     *   <li>{@code remoteHost} is a loopback address — defence in depth, since the manager
     *       agent is always a local subprocess.</li>
     * </ul>
     * Spoofing the markers without holding the token, or holding the token from off-host,
     * both fall back to {@code public}.
     */
    static boolean isTrustedInjectedViewRequest(
            String injectedViewHeader,
            String injectedTokenHeader,
            String remoteHost,
            String configuredToken) {
        if (configuredToken == null || configuredToken.isBlank()) {
            return false;
        }
        if (injectedViewHeader == null
                || !TRUSTED_VIEW_SOURCE_VALUE.equalsIgnoreCase(injectedViewHeader.trim())) {
            return false;
        }
        if (injectedTokenHeader == null) {
            return false;
        }
        if (!constantTimeEquals(injectedTokenHeader.trim(), configuredToken.trim())) {
            return false;
        }
        if (remoteHost == null) {
            return false;
        }
        return "127.0.0.1".equals(remoteHost) || "::1".equals(remoteHost) || "0:0:0:0:0:0:0:1".equals(remoteHost);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }

    private void logMcpPost(RoutingContext context, JsonNode request, String source, String resolvedToolName) {
        String method = request != null && request.hasNonNull("method") ? request.get("method").asText() : "";
        String toolName = resolvedToolName == null ? "" : resolvedToolName;
        log.info("[MCP] post path={} method={} source={} resolvedToolName={}",
                context.request().path(),
                method,
                source,
                toolName);
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
