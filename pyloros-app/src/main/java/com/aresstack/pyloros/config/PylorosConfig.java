package com.aresstack.pyloros.config;

import com.aresstack.pyloros.PylorosApplication;
import com.aresstack.pyloros.oauth.OAuthConfig;

import java.io.InputStream;
import java.util.Properties;

public record PylorosConfig(
        int serverPort,
        String serverHost,
        String publicOrigin,
        String mcpProtocolVersion,
        String securityMode,
        String targetPlatformModules,
        String centralOauthIssuer,
        String mcpInjectedViewToken
) implements ServerConfig, OAuthConfig {

    public static PylorosConfig load() {
        Properties properties = loadProperties();

        return new PylorosConfig(
                intValue("server.port", "SERVER_PORT", properties, 8081),
                // Loopback by default: the AresStack webserver (Caddy) is the only
                // intended caller; direct external access must not bypass it.
                value("server.host", "SERVER_HOST", properties, "127.0.0.1"),
                normalizeOrigin(value("public.origin", "PUBLIC_ORIGIN", properties, "https://current-car.com")),
                value("mcp.protocol.version", "MCP_VERSION_CHATGPT", properties, "2025-03-26"),
                // Secure default: validate centrally issued JWTs. Opt out with
                // SECURITY_MODE=none for unprotected local setups.
                value("security.mode", "SECURITY_MODE", properties, "central-oauth"),
                value("target.platform.modules", "TARGET_PLATFORM_MODULES", properties, "intellij"),
                normalizeOrigin(value("oauth.central-issuer", "CENTRAL_OAUTH_ISSUER", properties, "")),
                resolveInjectedViewToken(properties)
        );
    }

    private static String resolveInjectedViewToken(Properties properties) {
        String configured = value("mcp.injected-view.token", "MCP_INJECTED_VIEW_TOKEN", properties, "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
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
