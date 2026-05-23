package com.aresstack.pyloros.upstream.mcp;

public record McpUpstreamConfig(
        String providerId,
        boolean enabled,
        String transport,
        String url,
        String toolPrefix,
        String token,
        boolean requiresToken,
        int connectTimeoutMillis,
        int responseTimeoutMillis
) {
    public boolean isEnabled() {
        return enabled && providerId != null && !providerId.isBlank();
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public String normalizedPrefix() {
        if (toolPrefix == null || toolPrefix.isBlank()) {
            return providerId + "/";
        }
        return toolPrefix;
    }
}
