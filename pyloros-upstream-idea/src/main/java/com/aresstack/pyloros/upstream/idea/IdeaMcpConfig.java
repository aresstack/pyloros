package com.aresstack.pyloros.upstream.idea;

public record IdeaMcpConfig(
        boolean enabled,
        String host,
        int port,
        String ssePath,
        int connectTimeoutMillis,
        int responseTimeoutMillis,
        String toolPrefix
) {
}

