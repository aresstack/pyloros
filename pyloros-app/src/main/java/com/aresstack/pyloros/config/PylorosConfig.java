package com.aresstack.pyloros.config;

import com.aresstack.pyloros.PylorosApplication;
import com.aresstack.pyloros.upstream.github.GitHubMcpConfig;
import com.aresstack.pyloros.upstream.idea.IdeaMcpConfig;
import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;

import java.io.InputStream;
import java.util.Properties;

public record PylorosConfig(
        int serverPort,
        String publicOrigin,
        String mcpPublicPath,
        String mcpProtocolVersion,
        String oauthClientId,
        String oauthClientSecret,
        String fixedAccessToken,
        int oauthAccessTokenTtlSeconds,
        int oauthRefreshTokenTtlSeconds,
        boolean oauthRefreshTokenRotationEnabled,
        String oauthRefreshTokenStorePath
) implements ServerConfig {

    public static PylorosConfig load() {
        Properties properties = loadProperties();

        return new PylorosConfig(
                intValue("server.port", "SERVER_PORT", properties, 8081),
                normalizeOrigin(value("public.origin", "PUBLIC_ORIGIN", properties, "https://current-car.com")),
                value("mcp.public.path", "MCP_PUBLIC_PATH", properties, "/sse"),
                value("mcp.protocol.version", "MCP_VERSION_CHATGPT", properties, "2025-03-26"),
                value("oauth.client.id", "OAUTH_CLIENT_ID", properties, value("oauth.client.id", "BASIC_AUTH_USER", properties, "")),
                value("oauth.client.secret", "OAUTH_CLIENT_SECRET", properties, value("oauth.client.secret", "BASIC_AUTH_PASS", properties, "")),
                value("oauth.fixed-access-token", "OAUTH_ACCESS_TOKEN", properties, ""),
                intValue("oauth.access-token.ttl.seconds", "OAUTH_ACCESS_TOKEN_TTL_SECONDS", properties, 3600),
                intValue("oauth.refresh-token.ttl.seconds", "OAUTH_REFRESH_TOKEN_TTL_SECONDS", properties, 2592000),
                Boolean.parseBoolean(value("oauth.refresh-token.rotation.enabled", "OAUTH_REFRESH_TOKEN_ROTATION_ENABLED", properties, "false")),
                value("oauth.refresh-token.store.path", "OAUTH_REFRESH_TOKEN_STORE_PATH", properties, "data/oauth-refresh-tokens.json")
        );
    }

    public IdeaMcpConfig ideaMcpConfig() {
        Properties properties = loadProperties();

        boolean enabled = Boolean.parseBoolean(value("idea.mcp.enabled", "IDEA_MCP_ENABLED", properties, "true"));
        String host = value("idea.mcp.host", "IDEA_MCP_HOST", properties, "127.0.0.1");
        int port = intValue("idea.mcp.port", "IDEA_MCP_PORT", properties, 64343);
        String ssePath = value("idea.mcp.sse.path", "IDEA_MCP_SSE_PATH", properties, "/sse");
        int connectTimeout = intValue("idea.mcp.connect.timeout.ms", "IDEA_MCP_CONNECT_TIMEOUT_MS", properties, 3000);
        int responseTimeout = intValue("idea.mcp.response.timeout.ms", "IDEA_MCP_RESPONSE_TIMEOUT_MS", properties, 60000);
        String toolPrefix = value("idea.mcp.tool.prefix", "IDEA_MCP_TOOL_PREFIX", properties, "idea__");

        return new IdeaMcpConfig(enabled, host, port, ssePath, connectTimeout, responseTimeout, toolPrefix);
    }

    public GitHubMcpConfig githubMcpConfig() {
        Properties properties = loadProperties();
        McpUpstreamConfig upstream = githubUpstreamConfig();
        return new GitHubMcpConfig(
                upstream.enabled(),
                upstream.url(),
                upstream.token(),
                upstream.toolPrefix(),
                upstream.connectTimeoutMillis(),
                upstream.responseTimeoutMillis()
        );
    }

    public McpUpstreamConfig githubUpstreamConfig() {
        Properties properties = loadProperties();

        boolean enabled = Boolean.parseBoolean(valueWithFallback(
                "pyloros.upstream.github.enabled", "PYLOROS_UPSTREAM_GITHUB_ENABLED",
                "github.mcp.enabled", "GITHUB_MCP_ENABLED",
                properties, "false"
        ));
        String transport = value("pyloros.upstream.github.transport", "PYLOROS_UPSTREAM_GITHUB_TRANSPORT", properties, "streamable-http");
        String url = valueWithFallback(
                "pyloros.upstream.github.url", "PYLOROS_UPSTREAM_GITHUB_URL",
                "github.mcp.url", "GITHUB_MCP_URL",
                properties, "https://api.githubcopilot.com/mcp/"
        );
        String prefix = valueWithFallback(
                "pyloros.upstream.github.prefix", "PYLOROS_UPSTREAM_GITHUB_PREFIX",
                "github.mcp.tool.prefix", "GITHUB_MCP_TOOL_PREFIX",
                properties, "github/"
        );

        String tokenEnvName = value("pyloros.upstream.github.authorization-env", "PYLOROS_UPSTREAM_GITHUB_AUTHORIZATION_ENV", properties, "GITHUB_MCP_TOKEN");
        String token = System.getenv(tokenEnvName);
        if (token == null) {
            token = "";
        }

        int connectTimeout = intValueWithFallback(
                "pyloros.upstream.github.connect.timeout.ms", "PYLOROS_UPSTREAM_GITHUB_CONNECT_TIMEOUT_MS",
                "github.mcp.connect.timeout.ms", "GITHUB_MCP_CONNECT_TIMEOUT_MS",
                properties, 5000
        );
        int responseTimeout = intValueWithFallback(
                "pyloros.upstream.github.response.timeout.ms", "PYLOROS_UPSTREAM_GITHUB_RESPONSE_TIMEOUT_MS",
                "github.mcp.response.timeout.ms", "GITHUB_MCP_RESPONSE_TIMEOUT_MS",
                properties, 60000
        );

        return new McpUpstreamConfig("github", enabled, transport, url, prefix, token, true, connectTimeout, responseTimeout);
    }

    public McpUpstreamConfig intellijIndexUpstreamConfig() {
        Properties properties = loadProperties();

        boolean enabled = Boolean.parseBoolean(value("pyloros.upstream.intellij-index.enabled", "PYLOROS_UPSTREAM_INTELLIJ_INDEX_ENABLED", properties, "false"));
        String transport = value("pyloros.upstream.intellij-index.transport", "PYLOROS_UPSTREAM_INTELLIJ_INDEX_TRANSPORT", properties, "streamable-http");
        String url = value("pyloros.upstream.intellij-index.url", "PYLOROS_UPSTREAM_INTELLIJ_INDEX_URL", properties, "http://127.0.0.1:29170/index-mcp/streamable-http");
        String prefix = value("pyloros.upstream.intellij-index.prefix", "PYLOROS_UPSTREAM_INTELLIJ_INDEX_PREFIX", properties, "intellij-index/");
        int connectTimeout = intValue("pyloros.upstream.intellij-index.connect.timeout.ms", "PYLOROS_UPSTREAM_INTELLIJ_INDEX_CONNECT_TIMEOUT_MS", properties, 3000);
        int responseTimeout = intValue("pyloros.upstream.intellij-index.response.timeout.ms", "PYLOROS_UPSTREAM_INTELLIJ_INDEX_RESPONSE_TIMEOUT_MS", properties, 60000);

        return new McpUpstreamConfig("intellij-index", enabled, transport, url, prefix, "", false, connectTimeout, responseTimeout);
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = PylorosApplication.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load application.properties", exception);
        }
        return properties;
    }

    private static int intValue(String propertyName, String environmentName, Properties properties, int defaultValue) {
        return Integer.parseInt(value(propertyName, environmentName, properties, String.valueOf(defaultValue)));
    }

    private static int intValueWithFallback(String propertyNameA,
                                            String envNameA,
                                            String propertyNameB,
                                            String envNameB,
                                            Properties properties,
                                            int defaultValue) {
        return Integer.parseInt(valueWithFallback(propertyNameA, envNameA, propertyNameB, envNameB, properties, String.valueOf(defaultValue)));
    }

    private static String valueWithFallback(String propertyNameA,
                                            String envNameA,
                                            String propertyNameB,
                                            String envNameB,
                                            Properties properties,
                                            String defaultValue) {
        String first = value(propertyNameA, envNameA, properties, "");
        if (!first.isBlank()) {
            return first;
        }
        return value(propertyNameB, envNameB, properties, defaultValue);
    }

    private static String value(String propertyName, String environmentName, Properties properties, String defaultValue) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        String propertyValue = properties.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        return defaultValue;
    }

    private static String normalizeOrigin(String origin) {
        String normalizedOrigin = origin;
        while (normalizedOrigin.endsWith("/")) {
            normalizedOrigin = normalizedOrigin.substring(0, normalizedOrigin.length() - 1);
        }
        return normalizedOrigin;
    }
}
