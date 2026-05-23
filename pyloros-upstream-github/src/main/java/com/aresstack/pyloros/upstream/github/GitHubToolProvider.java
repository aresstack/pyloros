package com.aresstack.pyloros.upstream.github;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.tool.ToolProvider;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ToolProvider that forwards tool calls to the GitHub remote MCP server.
 * <p>
 * Tools are exposed using the {@code github/} namespace prefix.
 * If the GitHub token is missing or the provider is disabled, Pyloros still starts
 * and other providers are unaffected.
 */
public final class GitHubToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubToolProvider.class);

    private static final String PROVIDER_ID = "github";
    private static final String TOOL_PREFIX = "github/";

    private final GitHubMcpConfig config;
    private final GitHubMcpClient client;

    public GitHubToolProvider(GitHubMcpConfig config, GitHubMcpClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    /**
     * Strip the {@code github/} prefix to get the native tool name used by the GitHub MCP server.
     */
    @Override
    public String nativeToolName(String exposedToolName) {
        if (exposedToolName != null && exposedToolName.startsWith(TOOL_PREFIX)) {
            return exposedToolName.substring(TOOL_PREFIX.length());
        }
        return exposedToolName;
    }

    @Override
    public Future<List<Map<String, Object>>> listTools() {
        if (!config.enabled()) {
            return Future.succeededFuture(List.of());
        }
        if (config.token() == null || config.token().isBlank()) {
            log.info("[MCP-UPSTREAM] provider=github unavailable reason=token-not-configured");
            return Future.succeededFuture(List.of());
        }

        return client.listTools()
                .recover(err -> {
                    log.warn("[MCP-UPSTREAM] provider=github unavailable reason={}", err.getMessage());
                    return Future.succeededFuture(List.of());
                });
    }

    @Override
    public boolean supports(String toolName) {
        return config.enabled()
                && config.token() != null && !config.token().isBlank()
                && toolName != null && toolName.startsWith(TOOL_PREFIX);
    }

    @Override
    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        if (client == null) {
            return Future.succeededFuture(errorResult("GitHub MCP provider is not configured."));
        }

        String requestedName = toolCall.name() == null ? "" : toolCall.name();
        String nativeName = nativeToolName(requestedName);

        JsonObject arguments;
        try {
            String argStr = toolCall.arguments() == null ? "{}" : toolCall.arguments().toString();
            arguments = new JsonObject(argStr);
        } catch (Exception ex) {
            log.debug("[MCP-UPSTREAM] provider=github could not parse arguments for {}: {}", nativeName, ex.getMessage());
            arguments = new JsonObject();
        }

        log.info("[MCP-UPSTREAM] provider=github tools/call {} -> {}", requestedName, nativeName);

        return client.callTool(nativeName, arguments)
                .map(result -> {
                    if (result == null) {
                        return errorResult("GitHub MCP returned empty response for " + nativeName);
                    }
                    boolean isError = result.getBoolean("isError", false);
                    JsonArray content = result.getJsonArray("content");
                    if (content == null) {
                        String text = result.encode();
                        content = new JsonArray().add(new JsonObject().put("type", "text").put("text", text));
                    }
                    List<Object> contentList = new ArrayList<>();
                    for (int i = 0; i < content.size(); i++) {
                        Object item = content.getValue(i);
                        if (item instanceof JsonObject jo) {
                            contentList.add(jo.getMap());
                        } else {
                            contentList.add(item);
                        }
                    }
                    log.info("[MCP-UPSTREAM] provider=github tools/call {} returned {} content items (isError={})",
                            nativeName, contentList.size(), isError);
                    return (Map<String, Object>) Map.of("content", contentList, "isError", isError);
                })
                .recover(err -> {
                    log.warn("[MCP-UPSTREAM] provider=github tools/call {} failed: {}", nativeName, err.getMessage());
                    return Future.succeededFuture(errorResult(err.getMessage()));
                });
    }

    private static Map<String, Object> errorResult(String message) {
        return Map.of(
                "content", new Object[]{Map.of("type", "text", "text", message != null ? message : "Unknown error")},
                "isError", true
        );
    }
}

