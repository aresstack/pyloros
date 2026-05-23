package com.aresstack.pyloros.config;

import com.aresstack.pyloros.PylorosApplication;
import com.aresstack.pyloros.oauth.OAuthConfig;

import java.io.InputStream;
import java.util.Properties;

public record PylorosConfig(
        int serverPort,
        String publicOrigin,
        String mcpPublicPath,
        String mcpProtocolVersion,
        String securityMode,
        String oauthClientId,
        String oauthClientSecret,
        String fixedAccessToken,
        int oauthAccessTokenTtlSeconds,
        int oauthRefreshTokenTtlSeconds,
        boolean oauthRefreshTokenRotationEnabled,
        String oauthRefreshTokenStorePath
) implements ServerConfig, OAuthConfig {

    public static PylorosConfig load() {
        Properties properties = loadProperties();

        return new PylorosConfig(
                intValue("server.port", "SERVER_PORT", properties, 8081),
                normalizeOrigin(value("public.origin", "PUBLIC_ORIGIN", properties, "https://current-car.com")),
                value("mcp.public.path", "MCP_PUBLIC_PATH", properties, "/pyloros"),
                value("mcp.protocol.version", "MCP_VERSION_CHATGPT", properties, "2025-03-26"),
                value("security.mode", "SECURITY_MODE", properties, "oauth"),
                value("oauth.client.id", "OAUTH_CLIENT_ID", properties, value("oauth.client.id", "BASIC_AUTH_USER", properties, "")),
                value("oauth.client.secret", "OAUTH_CLIENT_SECRET", properties, value("oauth.client.secret", "BASIC_AUTH_PASS", properties, "")),
                value("oauth.fixed-access-token", "OAUTH_ACCESS_TOKEN", properties, ""),
                intValue("oauth.access-token.ttl.seconds", "OAUTH_ACCESS_TOKEN_TTL_SECONDS", properties, 3600),
                intValue("oauth.refresh-token.ttl.seconds", "OAUTH_REFRESH_TOKEN_TTL_SECONDS", properties, 2592000),
                Boolean.parseBoolean(value("oauth.refresh-token.rotation.enabled", "OAUTH_REFRESH_TOKEN_ROTATION_ENABLED", properties, "false")),
                value("oauth.refresh-token.store.path", "OAUTH_REFRESH_TOKEN_STORE_PATH", properties, "data/oauth-refresh-tokens.json")
        );
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
