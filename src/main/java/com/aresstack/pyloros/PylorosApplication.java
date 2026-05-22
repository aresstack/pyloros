package com.aresstack.pyloros;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.io.InputStream;

public final class PylorosApplication extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(PylorosApplication.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, AuthorizationCode> authorizationCodes = new LinkedHashMap<>();
    private final Map<String, AccessToken> accessTokens = new LinkedHashMap<>();

    private AppConfig config;

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new PylorosApplication())
                .onFailure(error -> {
                    log.error("Failed to deploy Pyloros", error);
                    System.exit(1);
                });
    }

    @Override
    public void start() {
        this.config = AppConfig.load();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/").handler(this::handleRoot);
        router.get("/health").handler(this::handleHealth);

        router.get("/.well-known/oauth-protected-resource").handler(this::handleProtectedResourceMetadata);
        router.get("/.well-known/oauth-protected-resource/sse").handler(this::handleProtectedResourceMetadata);

        router.get("/.well-known/oauth-authorization-server").handler(this::handleAuthorizationServerMetadata);
        router.get("/.well-known/openid-configuration").handler(this::handleAuthorizationServerMetadata);
        router.get("/.well-known/oauth-authorization-server/sse").handler(this::handleAuthorizationServerMetadata);
        router.get("/sse/.well-known/oauth-authorization-server").handler(this::handleAuthorizationServerMetadata);
        router.get("/.well-known/openid-configuration/sse").handler(this::handleAuthorizationServerMetadata);
        router.get("/sse/.well-known/openid-configuration").handler(this::handleAuthorizationServerMetadata);

        router.get("/oauth/authorize").handler(this::handleAuthorize);
        router.post("/oauth/token").handler(this::handleToken);

        router.get(config.mcpPublicPath()).handler(this::handleMcpSse);
        router.post(config.mcpPublicPath()).handler(this::handleMcpPost);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.serverPort())
                .onSuccess(server -> log.info("Pyloros listening on port {} with public URL {}{}", config.serverPort(), config.publicOrigin(), config.mcpPublicPath()))
                .onFailure(error -> log.error("Could not start HTTP server", error));
    }

    private void handleRoot(RoutingContext context) {
        json(context, 200, Map.of(
                "status", "ok",
                "name", "pyloros",
                "mcp", config.publicOrigin() + config.mcpPublicPath(),
                "authorization_endpoint", config.publicOrigin() + "/oauth/authorize",
                "token_endpoint", config.publicOrigin() + "/oauth/token",
                "advertised_pkce_method", "S256",
                "pkce_compatibility_mode", true
        ));
    }

    private void handleHealth(RoutingContext context) {
        json(context, 200, Map.of("status", "ok"));
    }

    private void handleProtectedResourceMetadata(RoutingContext context) {
        json(context, 200, Map.of(
                "resource", config.publicOrigin() + config.mcpPublicPath(),
                "authorization_servers", new String[]{config.publicOrigin()},
                "scopes_supported", new String[]{"mcp"},
                "bearer_methods_supported", new String[]{"header"}
        ));
    }

    private void handleAuthorizationServerMetadata(RoutingContext context) {
        json(context, 200, Map.of(
                "issuer", config.publicOrigin(),
                "authorization_endpoint", config.publicOrigin() + "/oauth/authorize",
                "token_endpoint", config.publicOrigin() + "/oauth/token",
                "response_types_supported", new String[]{"code"},
                "grant_types_supported", new String[]{"authorization_code"},
                "token_endpoint_auth_methods_supported", new String[]{"client_secret_basic", "client_secret_post"},
                "code_challenge_methods_supported", new String[]{"S256"},
                "scopes_supported", new String[]{"mcp"}
        ));
    }

    private void handleAuthorize(RoutingContext context) {
        String responseType = context.queryParam("response_type").stream().findFirst().orElse(null);
        String clientId = context.queryParam("client_id").stream().findFirst().orElse(null);
        String redirectUri = context.queryParam("redirect_uri").stream().findFirst().orElse(null);
        String state = context.queryParam("state").stream().findFirst().orElse(null);
        String scope = context.queryParam("scope").stream().findFirst().orElse("mcp");
        String codeChallenge = context.queryParam("code_challenge").stream().findFirst().orElse(null);
        String codeChallengeMethod = context.queryParam("code_challenge_method").stream().findFirst().orElse(null);

        log.info("[OAUTH] authorize responseType={} clientId={} redirectUri={} state={} scope={} codeChallengeMethod={} hasChallenge={}",
                responseType, clientId, redirectUri, state, scope, codeChallengeMethod, codeChallenge != null);

        if (!"code".equals(responseType)) {
            json(context, 400, Map.of("error", "unsupported_response_type"));
            return;
        }

        if (!Objects.equals(clientId, config.oauthClientId())) {
            json(context, 400, Map.of("error", "invalid_client"));
            return;
        }

        if (redirectUri == null || redirectUri.isBlank()) {
            json(context, 400, Map.of("error", "invalid_request", "error_description", "Missing redirect_uri"));
            return;
        }

        if (!isAllowedRedirectUri(redirectUri)) {
            json(context, 400, Map.of("error", "invalid_request", "error_description", "Unsupported redirect_uri"));
            return;
        }

        if (codeChallenge == null || !"S256".equals(codeChallengeMethod)) {
            log.info("[OAUTH] PKCE S256 is missing. Continue in compatibility mode. hasChallenge={} method={}", codeChallenge != null, codeChallengeMethod);
        }

        String code = createOpaqueToken(24);
        authorizationCodes.put(code, new AuthorizationCode(
                clientId,
                redirectUri,
                scope,
                codeChallenge,
                codeChallengeMethod,
                Instant.now(Clock.systemUTC()).plusSeconds(300)
        ));

        String location = appendQuery(redirectUri, "code", code, state);
        context.response()
                .setStatusCode(302)
                .putHeader(HttpHeaders.LOCATION, location)
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .end();
    }

    private void handleToken(RoutingContext context) {
        ClientCredentials credentials = readClientCredentials(context);
        String grantType = context.request().getFormAttribute("grant_type");
        String code = context.request().getFormAttribute("code");
        String redirectUri = context.request().getFormAttribute("redirect_uri");
        String codeVerifier = context.request().getFormAttribute("code_verifier");

        log.info("[OAUTH] token grantType={} code={} redirectUri={} clientId={} authType={} hasVerifier={}",
                grantType, code, redirectUri, credentials.clientId(), authorizationType(context), codeVerifier != null);

        if (!Objects.equals(credentials.clientId(), config.oauthClientId()) || !Objects.equals(credentials.clientSecret(), config.oauthClientSecret())) {
            json(context, 401, Map.of("error", "invalid_client"));
            return;
        }

        if (!"authorization_code".equals(grantType)) {
            json(context, 400, Map.of("error", "unsupported_grant_type"));
            return;
        }

        AuthorizationCode authorizationCode = authorizationCodes.remove(code);
        if (authorizationCode == null) {
            json(context, 400, Map.of("error", "invalid_grant"));
            return;
        }

        if (authorizationCode.expiresAt().isBefore(Instant.now(Clock.systemUTC()))) {
            json(context, 400, Map.of("error", "invalid_grant", "error_description", "Authorization code expired"));
            return;
        }

        if (!Objects.equals(authorizationCode.clientId(), credentials.clientId())) {
            json(context, 400, Map.of("error", "invalid_grant"));
            return;
        }

        if (redirectUri != null && !Objects.equals(redirectUri, authorizationCode.redirectUri())) {
            json(context, 400, Map.of("error", "invalid_grant", "error_description", "redirect_uri mismatch"));
            return;
        }

        if (!isPkceValid(codeVerifier, authorizationCode)) {
            json(context, 400, Map.of("error", "invalid_grant", "error_description", "PKCE verification failed"));
            return;
        }

        String accessToken = config.fixedAccessToken().isBlank() ? createOpaqueToken(32) : config.fixedAccessToken();
        accessTokens.put(accessToken, new AccessToken(authorizationCode.scope(), Instant.now(Clock.systemUTC()).plusSeconds(3600)));

        json(context, 200, Map.of(
                "access_token", accessToken,
                "token_type", "Bearer",
                "expires_in", 3600,
                "scope", authorizationCode.scope()
        ));
    }

    private void handleMcpSse(RoutingContext context) {
        if (!isBearerAuthorized(context)) {
            unauthorized(context);
            return;
        }

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .putHeader(HttpHeaders.CONNECTION, "keep-alive");
        context.response().write("event: endpoint\n");
        context.response().write("data: " + config.mcpPublicPath() + "\n\n");
    }

    private void handleMcpPost(RoutingContext context) {
        if (!isBearerAuthorized(context)) {
            unauthorized(context);
            return;
        }

        JsonNode request;
        try {
            request = MAPPER.readTree(context.body().asString());
        } catch (JsonProcessingException exception) {
            json(context, 400, Map.of("error", "Invalid JSON"));
            return;
        }

        JsonNode id = request.get("id");
        String method = request.hasNonNull("method") ? request.get("method").asText() : null;
        log.info("[MCP] RPC {}", method);

        if (id == null || id.isNull()) {
            json(context, 202, Map.of("status", "accepted"));
            return;
        }

        switch (method == null ? "" : method) {
            case "initialize" -> jsonRpcResult(context, id, Map.of(
                    "protocolVersion", config.mcpProtocolVersion(),
                    "capabilities", Map.of("tools", Map.of(), "resources", Map.of(), "prompts", Map.of()),
                    "serverInfo", Map.of("name", "pyloros", "version", "0.1.0")
            ));
            case "tools/list" -> jsonRpcResult(context, id, Map.of("tools", new Object[]{dummyTool()}));
            case "resources/list" -> jsonRpcResult(context, id, Map.of("resources", new Object[]{}));
            case "prompts/list" -> jsonRpcResult(context, id, Map.of("prompts", new Object[]{}));
            case "tools/call", "call_tool" -> jsonRpcResult(context, id, Map.of(
                    "content", new Object[]{Map.of("type", "text", "text", "Pyloros Java gateway is alive.")},
                    "isError", false
            ));
            default -> jsonRpcError(context, id, -32601, "Method not supported");
        }
    }

    private Map<String, Object> dummyTool() {
        return Map.of(
                "name", "pyloros__ping",
                "description", "Returns a small confirmation that Pyloros is alive.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                ),
                "securitySchemes", new Object[]{Map.of("type", "oauth2", "scopes", new String[]{"mcp"})},
                "_meta", Map.of("securitySchemes", new Object[]{Map.of("type", "oauth2", "scopes", new String[]{"mcp"})})
        );
    }

    private boolean isBearerAuthorized(RoutingContext context) {
        String authorization = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (!config.fixedAccessToken().isBlank() && Objects.equals(token, config.fixedAccessToken())) {
            return true;
        }

        AccessToken accessToken = accessTokens.get(token);
        if (accessToken == null) {
            return false;
        }

        if (accessToken.expiresAt().isBefore(Instant.now(Clock.systemUTC()))) {
            accessTokens.remove(token);
            return false;
        }

        return true;
    }

    private void unauthorized(RoutingContext context) {
        context.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Bearer realm=\"pyloros\", resource_metadata=\"" + config.publicOrigin() + "/.well-known/oauth-protected-resource\"")
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .end("{\"error\":\"Unauthorized\"}");
    }

    private boolean isPkceValid(String codeVerifier, AuthorizationCode authorizationCode) {
        if (authorizationCode.codeChallenge() == null || authorizationCode.codeChallenge().isBlank()) {
            log.info("[OAUTH] Skip PKCE validation because no code challenge was provided.");
            return true;
        }

        if (codeVerifier == null || codeVerifier.isBlank()) {
            return false;
        }

        if ("S256".equals(authorizationCode.codeChallengeMethod())) {
            String calculated = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sha256(codeVerifier));
            return Objects.equals(calculated, authorizationCode.codeChallenge());
        }

        return Objects.equals(codeVerifier, authorizationCode.codeChallenge());
    }

    private byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ClientCredentials readClientCredentials(RoutingContext context) {
        String authorization = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Basic ")) {
            String encoded = authorization.substring("Basic ".length()).trim();
            String decoded = new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
            int separatorIndex = decoded.indexOf(':');
            if (separatorIndex >= 0) {
                return new ClientCredentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
            }
        }

        String clientId = context.request().getFormAttribute("client_id");
        String clientSecret = context.request().getFormAttribute("client_secret");
        return new ClientCredentials(nullToEmpty(clientId), nullToEmpty(clientSecret));
    }

    private String authorizationType(RoutingContext context) {
        String authorization = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return "none";
        }
        if (authorization.startsWith("Basic ")) {
            return "basic";
        }
        if (authorization.startsWith("Bearer ")) {
            return "bearer";
        }
        return "other";
    }

    private boolean isAllowedRedirectUri(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && ("chatgpt.com".equalsIgnoreCase(uri.getHost()) || "chat.openai.com".equalsIgnoreCase(uri.getHost()));
        } catch (Exception exception) {
            return false;
        }
    }

    private String appendQuery(String redirectUri, String codeParameter, String code, String state) {
        String separator = redirectUri.contains("?") ? "&" : "?";
        StringBuilder builder = new StringBuilder(redirectUri).append(separator).append(codeParameter).append('=').append(urlEncode(code));
        if (state != null && !state.isBlank()) {
            builder.append('&').append("state=").append(urlEncode(state));
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void jsonRpcResult(RoutingContext context, JsonNode id, Object result) {
        json(context, 200, Map.of("jsonrpc", "2.0", "id", MAPPER.convertValue(id, Object.class), "result", result));
    }

    private void jsonRpcError(RoutingContext context, JsonNode id, int code, String message) {
        json(context, 200, Map.of(
                "jsonrpc", "2.0",
                "id", MAPPER.convertValue(id, Object.class),
                "error", Map.of("code", code, "message", message)
        ));
    }

    private void json(RoutingContext context, int statusCode, Object body) {
        try {
            context.response()
                    .setStatusCode(statusCode)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .end(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException exception) {
            context.fail(exception);
        }
    }

    private String createOpaqueToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record AuthorizationCode(
            String clientId,
            String redirectUri,
            String scope,
            String codeChallenge,
            String codeChallengeMethod,
            Instant expiresAt
    ) {
    }

    private record AccessToken(String scope, Instant expiresAt) {
    }

    private record ClientCredentials(String clientId, String clientSecret) {
    }

    private record AppConfig(
            int serverPort,
            String publicOrigin,
            String mcpPublicPath,
            String mcpProtocolVersion,
            String oauthClientId,
            String oauthClientSecret,
            String fixedAccessToken
    ) {
        static AppConfig load() {
            Properties properties = new Properties();
            try (InputStream inputStream = PylorosApplication.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (inputStream != null) {
                    properties.load(inputStream);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load application.properties", exception);
            }

            return new AppConfig(
                    intValue("server.port", "SERVER_PORT", properties, 8081),
                    normalizeOrigin(value("public.origin", "PUBLIC_ORIGIN", properties, "https://current-car.com")),
                    value("mcp.public.path", "MCP_PUBLIC_PATH", properties, "/sse"),
                    value("mcp.protocol.version", "MCP_VERSION_CHATGPT", properties, "2025-03-26"),
                    value("oauth.client.id", "OAUTH_CLIENT_ID", properties, value("oauth.client.id", "BASIC_AUTH_USER", properties, "")),
                    value("oauth.client.secret", "OAUTH_CLIENT_SECRET", properties, value("oauth.client.secret", "BASIC_AUTH_PASS", properties, "")),
                    value("oauth.fixed-access-token", "OAUTH_ACCESS_TOKEN", properties, "")
            );
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
            while (origin.endsWith("/")) {
                origin = origin.substring(0, origin.length() - 1);
            }
            return origin;
        }
    }
}
