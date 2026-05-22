package com.aresstack.pyloros.oauth;

import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.domain.oauth.AccessToken;
import com.aresstack.pyloros.domain.oauth.AuthorizationCode;
import com.aresstack.pyloros.domain.oauth.ClientCredentials;
import com.aresstack.pyloros.domain.oauth.OAuthException;
import com.aresstack.pyloros.domain.oauth.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int REFRESH_STORE_VERSION = 1;

    private final PylorosConfig config;
    private final Clock clock;
    private final Map<String, AuthorizationCode> authorizationCodes = new LinkedHashMap<>();
    private final Map<String, AccessToken> accessTokens = new LinkedHashMap<>();
    private final Map<String, RefreshTokenState> refreshTokens = new LinkedHashMap<>();

    public OAuthService(PylorosConfig config) {
        this(config, Clock.systemUTC());
    }

    OAuthService(PylorosConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        loadRefreshTokensFromStore();
        cleanupExpiredRefreshTokens();
    }

    public String authorize(String responseType,
                            String clientId,
                            String redirectUri,
                            String state,
                            String scope,
                            String codeChallenge,
                            String codeChallengeMethod) {
        log.info("[OAUTH] authorize responseType={} clientId={} redirectUri={} state={} scope={} codeChallengeMethod={} hasChallenge={}",
                responseType, clientId, redirectUri, state, scope, codeChallengeMethod, codeChallenge != null);

        if (!"code".equals(responseType)) {
            throw new OAuthException(400, "unsupported_response_type");
        }

        if (!Objects.equals(clientId, config.oauthClientId())) {
            throw new OAuthException(400, "invalid_client");
        }

        if (redirectUri == null || redirectUri.isBlank()) {
            throw new OAuthException(400, "invalid_request", "Missing redirect_uri");
        }

        if (!isAllowedRedirectUri(redirectUri)) {
            throw new OAuthException(400, "invalid_request", "Unsupported redirect_uri");
        }

        if (codeChallenge == null || !"S256".equals(codeChallengeMethod)) {
            log.info("[OAUTH] PKCE S256 is missing. Continue in compatibility mode. hasChallenge={} method={}", codeChallenge != null, codeChallengeMethod);
        }

        String code = createOpaqueValue();
        authorizationCodes.put(code, new AuthorizationCode(
                clientId,
                redirectUri,
                scope == null || scope.isBlank() ? "mcp" : scope,
                codeChallenge,
                codeChallengeMethod,
                Instant.now(clock).plusSeconds(300)
        ));

        return appendQuery(redirectUri, code, state);
    }

    public TokenResponse exchangeAuthorizationCode(ClientCredentials credentials,
                                                   String grantType,
                                                   String code,
                                                   String refreshToken,
                                                   String redirectUri,
                                                   String codeVerifier) {
        log.info("[OAUTH] token grantType={} code={} redirectUri={} clientId={} hasVerifier={}",
                grantType, code, redirectUri, credentials.clientId(), codeVerifier != null);

        if (!isKnownOAuthClient(credentials)) {
            throw new OAuthException(401, "invalid_client");
        }

        cleanupExpiredRefreshTokens();

        if ("authorization_code".equals(grantType)) {
            return exchangeFromAuthorizationCode(credentials, code, redirectUri, codeVerifier);
        }
        if ("refresh_token".equals(grantType)) {
            return exchangeFromRefreshToken(credentials, refreshToken);
        }

        throw new OAuthException(400, "unsupported_grant_type");
    }

    private TokenResponse exchangeFromAuthorizationCode(ClientCredentials credentials,
                                                        String code,
                                                        String redirectUri,
                                                        String codeVerifier) {

        AuthorizationCode authorizationCode = authorizationCodes.remove(code);
        if (authorizationCode == null) {
            throw new OAuthException(400, "invalid_grant");
        }

        if (authorizationCode.expiresAt().isBefore(Instant.now(clock))) {
            throw new OAuthException(400, "invalid_grant", "Authorization code expired");
        }

        if (!Objects.equals(authorizationCode.clientId(), credentials.clientId())) {
            throw new OAuthException(400, "invalid_grant");
        }

        if (redirectUri != null && !Objects.equals(redirectUri, authorizationCode.redirectUri())) {
            throw new OAuthException(400, "invalid_grant", "redirect_uri mismatch");
        }

        if (!isPkceValid(codeVerifier, authorizationCode)) {
            throw new OAuthException(400, "invalid_grant", "PKCE verification failed");
        }

        String accessToken = config.fixedAccessToken().isBlank() ? createOpaqueValue() : config.fixedAccessToken();
        String refreshToken = createOpaqueValue();
        int expiresIn = config.oauthAccessTokenTtlSeconds();
        int refreshExpiresIn = config.oauthRefreshTokenTtlSeconds();
        Instant now = Instant.now(clock);
        accessTokens.put(accessToken, new AccessToken(authorizationCode.scope(), now.plusSeconds(expiresIn)));
        refreshTokens.put(refreshToken, new RefreshTokenState(
                credentials.clientId(),
                authorizationCode.scope(),
                now.plusSeconds(refreshExpiresIn)
        ));
        saveRefreshTokensToStore();

        return new TokenResponse(accessToken, "Bearer", expiresIn, refreshToken, authorizationCode.scope());
    }

    private TokenResponse exchangeFromRefreshToken(ClientCredentials credentials, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new OAuthException(400, "invalid_grant");
        }

        RefreshTokenState state = refreshTokens.get(refreshToken);
        if (state == null) {
            throw new OAuthException(400, "invalid_grant");
        }

        Instant now = Instant.now(clock);
        if (state.expiresAt().isBefore(now)) {
            refreshTokens.remove(refreshToken);
            saveRefreshTokensToStore();
            throw new OAuthException(400, "invalid_grant");
        }

        if (!Objects.equals(state.clientId(), credentials.clientId())) {
            refreshTokens.remove(refreshToken);
            saveRefreshTokensToStore();
            throw new OAuthException(400, "invalid_grant");
        }

        String accessToken = config.fixedAccessToken().isBlank() ? createOpaqueValue() : config.fixedAccessToken();
        int expiresIn = config.oauthAccessTokenTtlSeconds();
        accessTokens.put(accessToken, new AccessToken(state.scope(), now.plusSeconds(expiresIn)));

        String nextRefreshToken = refreshToken;
        if (config.oauthRefreshTokenRotationEnabled()) {
            refreshTokens.remove(refreshToken);
            nextRefreshToken = createOpaqueValue();
            refreshTokens.put(nextRefreshToken, new RefreshTokenState(
                    credentials.clientId(),
                    state.scope(),
                    now.plusSeconds(config.oauthRefreshTokenTtlSeconds())
            ));
            saveRefreshTokensToStore();
        }

        return new TokenResponse(accessToken, "Bearer", expiresIn, nextRefreshToken, state.scope());
    }

    public boolean isBearerAuthorized(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (!config.fixedAccessToken().isBlank() && Objects.equals(token, config.fixedAccessToken())) {
            return true;
        }

        AccessToken accessToken = accessTokens.get(token);
        if (accessToken == null) {
            return false;
        }

        if (accessToken.expiresAt().isBefore(Instant.now(clock))) {
            accessTokens.remove(token);
            return false;
        }

        return true;
    }

    private boolean isKnownOAuthClient(ClientCredentials credentials) {
        return Objects.equals(credentials.clientId(), config.oauthClientId())
                && Objects.equals(credentials.clientSecret(), config.oauthClientSecret());
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

    private boolean isPkceValid(String codeVerifier, AuthorizationCode authorizationCode) {
        if (authorizationCode.codeChallenge() == null || authorizationCode.codeChallenge().isBlank()) {
            log.info("[OAUTH] Skip PKCE validation because no code challenge was provided.");
            return true;
        }

        if (codeVerifier == null || codeVerifier.isBlank()) {
            return false;
        }

        if ("S256".equals(authorizationCode.codeChallengeMethod())) {
            String calculated = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(codeVerifier));
            return Objects.equals(calculated, authorizationCode.codeChallenge());
        }

        return Objects.equals(codeVerifier, authorizationCode.codeChallenge());
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String appendQuery(String redirectUri, String code, String state) {
        String separator = redirectUri.contains("?") ? "&" : "?";
        StringBuilder builder = new StringBuilder(redirectUri)
                .append(separator)
                .append("code=")
                .append(urlEncode(code));
        if (state != null && !state.isBlank()) {
            builder.append('&').append("state=").append(urlEncode(state));
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String createOpaqueValue() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private void cleanupExpiredRefreshTokens() {
        Instant now = Instant.now(clock);
        boolean changed = refreshTokens.entrySet().removeIf(entry -> {
            RefreshTokenState state = entry.getValue();
            return state == null || state.expiresAt().isBefore(now);
        });
        if (changed) {
            saveRefreshTokensToStore();
        }
    }

    private void loadRefreshTokensFromStore() {
        try {
            Path storePath = Path.of(config.oauthRefreshTokenStorePath());
            if (!Files.exists(storePath)) {
                return;
            }

            RefreshTokenStoreDocument document = JSON.readValue(storePath.toFile(), RefreshTokenStoreDocument.class);
            if (document == null || document.tokens() == null) {
                return;
            }

            for (RefreshTokenEntry entry : document.tokens()) {
                if (entry == null || entry.token() == null || entry.token().isBlank()) {
                    continue;
                }
                if (entry.clientId() == null || entry.clientId().isBlank()) {
                    continue;
                }
                if (entry.scope() == null || entry.scope().isBlank()) {
                    continue;
                }
                if (entry.expiresAt() == null || entry.expiresAt().isBlank()) {
                    continue;
                }

                Instant expiresAt;
                try {
                    expiresAt = Instant.parse(entry.expiresAt());
                } catch (Exception parseEx) {
                    continue;
                }

                refreshTokens.put(entry.token(), new RefreshTokenState(entry.clientId(), entry.scope(), expiresAt));
            }

            log.info("[OAUTH] Loaded {} refresh tokens from {}", refreshTokens.size(), storePath);
        } catch (Exception ex) {
            log.warn("[OAUTH] Failed to load refresh token store: {}", ex.getMessage());
        }
    }

    private void saveRefreshTokensToStore() {
        try {
            Path storePath = Path.of(config.oauthRefreshTokenStorePath());
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            List<RefreshTokenEntry> entries = new ArrayList<>();
            for (Map.Entry<String, RefreshTokenState> mapEntry : refreshTokens.entrySet()) {
                RefreshTokenState state = mapEntry.getValue();
                if (state == null) {
                    continue;
                }
                entries.add(new RefreshTokenEntry(
                        mapEntry.getKey(),
                        state.clientId(),
                        state.scope(),
                        state.expiresAt().toString()
                ));
            }

            RefreshTokenStoreDocument doc = new RefreshTokenStoreDocument(REFRESH_STORE_VERSION, entries);

            Path tempPath = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            JSON.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), doc);
            try {
                Files.move(tempPath, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(tempPath, storePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            log.warn("[OAUTH] Failed to save refresh token store: {}", ex.getMessage());
        }
    }

    private record RefreshTokenState(String clientId, String scope, Instant expiresAt) {
    }

    private record RefreshTokenStoreDocument(int version, List<RefreshTokenEntry> tokens) {
    }

    private record RefreshTokenEntry(String token, String clientId, String scope, String expiresAt) {
    }
}
