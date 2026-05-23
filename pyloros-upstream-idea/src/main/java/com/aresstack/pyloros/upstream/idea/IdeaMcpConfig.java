package com.aresstack.pyloros.upstream.idea;

import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record IdeaMcpConfig(
        URI sseUrl,
        Map<String, String> headers,
        int connectTimeoutMillis,
        int responseTimeoutMillis
) {
    public static IdeaMcpConfig from(McpUpstreamConfig config) {
        return new IdeaMcpConfig(
                config.url(),
                config.headers(),
                config.connectTimeoutMillis(),
                config.responseTimeoutMillis()
        );
    }

    public IdeaMcpConfig {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers == null ? Map.of() : headers));
    }

    public boolean enabled() {
        return sseUrl != null;
    }

    public String host() {
        return sseUrl.getHost();
    }

    public int port() {
        return sseUrl.getPort() < 0 ? ("https".equalsIgnoreCase(sseUrl.getScheme()) ? 443 : 80) : sseUrl.getPort();
    }

    public String ssePath() {
        String path = sseUrl.getPath();
        return path == null || path.isBlank() ? "/" : path;
    }
}
