package com.aresstack.pyloros.upstream.github;

public record GitHubMcpConfig(
        boolean enabled,
        String url,
        String token,
        String toolPrefix,
        int connectTimeoutMillis,
        int responseTimeoutMillis
) {
}

