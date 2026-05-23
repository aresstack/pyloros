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

    public ToolRouter(ProviderRegistry providerRegistry, ToolCatalog toolCatalog) {
        this.providerRegistry = providerRegistry;
        this.toolCatalog = toolCatalog;
    }

    public Future<Map<String, Object>> callTool(McpToolCall toolCall) {
        if (toolCall == null || toolCall.name() == null || toolCall.name().isBlank()) {
            log.info("[TOOL-ROUTER] catalog lookup externalName={} hit=false", "null");
            return toolNotFound("null");
        }

        String requestedExternalName = toolCall.name();
        ToolCatalogEntry entry = toolCatalog.findByExternalName(requestedExternalName).orElse(null);
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
