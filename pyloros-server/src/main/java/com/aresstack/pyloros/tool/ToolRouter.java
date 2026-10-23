package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);

    private final ProviderRegistry providerRegistry;
    private final ToolCatalog toolCatalog;
    private final boolean enforceToolScopes;

    /**
     * Keeps per-tool scopes declared but unenforced.
     *
     * <p>The deployed ChatGPT connector provably requests a single scope ({@code mcp},
     * see the authorization requests in the reverse proxy log), so enforcing a tool's
     * own scope by default would instantly deny every writing tool. Enforcement is
     * therefore opt-in until a client actually requests the finer scopes.
     */
    public ToolRouter(ProviderRegistry providerRegistry, ToolCatalog toolCatalog) {
        this(providerRegistry, toolCatalog, false);
    }

    public ToolRouter(ProviderRegistry providerRegistry, ToolCatalog toolCatalog,
                      boolean enforceToolScopes) {
        this.providerRegistry = providerRegistry;
        this.toolCatalog = toolCatalog;
        this.enforceToolScopes = enforceToolScopes;
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        return callTool(toolCall, ToolView.PUBLIC);
    }

    /**
     * Dispatches a call and enforces the tool's own scope requirement.
     *
     * <p>The gateway only checks the service-wide scope. Without this step a token
     * that is good enough to reach Pyloros would be good enough for every tool,
     * which is why a finer permission model could be configured but never enforced.
     *
     * <p>Only active when per-tool enforcement is switched on; otherwise the call is
     * dispatched exactly as before.
     */
    public Future<Map<String, Object>> callTool(McpToolCall toolCall, ToolView toolView,
                                                java.util.List<String> grantedScopes) {
        if (enforceToolScopes && toolCall != null && toolCall.name() != null) {
            String required = requiredScopeFor(toolCall.name(), toolView);
            if (required != null && !required.isBlank()
                    && (grantedScopes == null || !grantedScopes.contains(required))) {
                log.info("[TOOL-ROUTER] denied externalName={} missingScope={}",
                        toolCall.name(), required);
                return Future.succeededFuture(Map.of(
                        "content", List.of(Map.of("type", "text",
                                "text", "Permission denied: this tool requires the scope '"
                                        + required + "'.")),
                        "isError", true));
            }
        }
        return callTool(toolCall, toolView);
    }

    /** The scope the owning provider demands for this external tool name. */
    private String requiredScopeFor(String externalName, ToolView toolView) {
        ToolCatalogEntry entry = toolCatalog.cachedSnapshotForView(
                        toolView == null ? ToolView.PUBLIC : toolView)
                .findByExternalName(externalName).orElse(null);
        if (entry == null) {
            return null;
        }
        return providerRegistry.findById(entry.address().providerId())
                .map(provider -> provider.requiredScope(entry.address().upstreamToolName()))
                .orElse(null);
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall, ToolView toolView) {
        if (toolCall == null || toolCall.name() == null || toolCall.name().isBlank()) {
            log.info("[TOOL-ROUTER] catalog lookup externalName={} hit=false", "null");
            return toolNotFound("null");
        }

        String requestedExternalName = toolCall.name();
        ToolView requestedView = toolView == null ? ToolView.PUBLIC : toolView;
        ToolCatalogSnapshot cachedSnapshot = toolCatalog.cachedSnapshotForView(requestedView);
        ToolCatalogEntry cachedEntry = cachedSnapshot.findByExternalName(requestedExternalName).orElse(null);
        if (cachedEntry != null) {
            return dispatchWithEntry(requestedExternalName, toolCall, cachedEntry);
        }

        return toolCatalog.refreshForView(requestedView)
                .compose(snapshot -> dispatch(requestedExternalName, toolCall, snapshot));
    }

    private Future<Map<String, Object>> dispatch(String requestedExternalName, McpToolCall toolCall) {
        ToolCatalogEntry entry = toolCatalog.findByExternalName(requestedExternalName).orElse(null);
        return dispatchWithEntry(requestedExternalName, toolCall, entry);
    }

    private Future<Map<String, Object>> dispatch(String requestedExternalName, McpToolCall toolCall, ToolCatalogSnapshot snapshot) {
        ToolCatalogEntry entry = snapshot.findByExternalName(requestedExternalName).orElse(null);
        return dispatchWithEntry(requestedExternalName, toolCall, entry);
    }

    private Future<Map<String, Object>> dispatchWithEntry(String requestedExternalName, McpToolCall toolCall, ToolCatalogEntry entry) {
        if (entry == null) {
            log.info("[TOOL-ROUTER] catalog lookup externalName={} hit=false", requestedExternalName);
            return toolNotFound(requestedExternalName);
        }

        ToolAddress address = entry.address();
        log.info("[TOOL-ROUTER] catalog lookup externalName={} hit=true providerId={} upstreamToolName={}",
                requestedExternalName,
                address.providerId(),
                address.upstreamToolName());
        return providerRegistry.findById(address.providerId())
                .map(provider -> {
                    log.info("[TOOL-ROUTER] provider dispatch providerId={} upstreamToolName={}",
                            address.providerId(),
                            address.upstreamToolName());
                    return provider.callTool(address.upstreamToolName(), toolCall.arguments());
                })
                .orElseGet(() -> {
                    log.warn("[TOOL-ROUTER] provider missing for providerId={} externalName={}",
                            address.providerId(),
                            requestedExternalName);
                    return toolNotFound(requestedExternalName);
                });
    }

    private static Future<Map<String, Object>> toolNotFound(String toolName) {
        return Future.succeededFuture(Map.of(
                "content", List.of(Map.of("type", "text", "text", "Tool not found: " + toolName)),
                "isError", true
        ));
    }
}
