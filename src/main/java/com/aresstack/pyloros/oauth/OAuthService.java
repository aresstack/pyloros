package com.aresstack.pyloros.oauth;

import com.aresstack.pyloros.config.PylorosConfig;
import com.aresstack.pyloros.domain.oauth.AccessToken;
import com.aresstack.pyloros.domain.oauth.AuthorizationCode;
import com.aresstack.pyloros.domain.oauth.BearerAuthResult;
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
    private final Map<String, ReplayCacheEntry> replayCache = new LinkedHashMap<>();
    private final Map<String, RefreshReplayCacheEntry> refreshReplayCache = new LinkedHashMap<>();

    private static final int REPLAY_CACHE_TTL_SECONDS = 10;

    public OAuthService(PylorosConfig config) {
        this(config, Clock.systemUTC());
    }

    OAuthService(PylorosConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        loadRefreshTokensFromStore();
        cleanupExpiredRefreshTokens();
        cleanupExpiredRefreshReplayCache();
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
        cleanupExpiredReplayCache();
        cleanupExpiredRefreshReplayCache();

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
        cleanupExpiredReplayCache();

        AuthorizationCode authorizationCode = authorizationCodes.remove(code);
        if (authorizationCode == null) {
            return tryReplayAuthorization(credentials, code, redirectUri, codeVerifier);
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

        TokenResponse tokenResponse = new TokenResponse(accessToken, "Bearer", expiresIn, refreshToken, authorizationCode.scope());

        // Store in replay cache to tolerate duplicate parallel requests within a short window
        replayCache.put(code, new ReplayCacheEntry(
                tokenResponse,
                credentials.clientId(),
                redirectUri,
                codeVerifier,
                now.plusSeconds(REPLAY_CACHE_TTL_SECONDS)
        ));

        return tokenResponse;
    }

    private TokenResponse tryReplayAuthorization(ClientCredentials credentials,
                                                 String code,
                                                 String redirectUri,
                                                 String codeVerifier) {
        ReplayCacheEntry entry = replayCache.get(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now(clock))) {
            log.info("[OAUTH] authorization_code replay miss code={}", shortValue(code));
            throw new OAuthException(400, "invalid_grant");
        }

        if (!Objects.equals(entry.clientId(), credentials.clientId())) {
            log.warn("[OAUTH] authorization_code replay rejected - client_id mismatch code={}", shortValue(code));
            throw new OAuthException(400, "invalid_grant");
        }

        if (redirectUri != null && !Objects.equals(redirectUri, entry.redirectUri())) {
            log.warn("[OAUTH] authorization_code replay rejected - redirect_uri mismatch code={}", shortValue(code));
            throw new OAuthException(400, "invalid_grant", "redirect_uri mismatch");
        }

        if (!Objects.equals(codeVerifier, entry.codeVerifier())) {
            log.warn("[OAUTH] authorization_code replay rejected - code_verifier mismatch code={}", shortValue(code));
            throw new OAuthException(400, "invalid_grant", "PKCE verification failed");
        }

        log.info("[OAUTH] authorization_code replay hit code={} clientId={}", shortValue(code), credentials.clientId());
        return entry.tokenResponse();
    }

    private void cleanupExpiredReplayCache() {
        Instant now = Instant.now(clock);
        replayCache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private static String shortValue(String value) {
        if (value == null) return "null";
        return value.length() > 8 ? value.substring(0, 8) + "..." : value;
    }

    private TokenResponse exchangeFromRefreshToken(ClientCredentials credentials, String refreshToken) {
        cleanupExpiredRefreshReplayCache();

        if (refreshToken == null || refreshToken.isBlank()) {
            log.info("[OAUTH] refresh rejected reason=missing_refresh_token clientId={}", credentials.clientId());
            throw new OAuthException(400, "invalid_grant");
        }

        RefreshTokenState state = refreshTokens.get(refreshToken);
        if (state == null) {
            return tryReplayRefreshToken(credentials, refreshToken);
        }

        Instant now = Instant.now(clock);
        if (state.expiresAt().isBefore(now)) {
            refreshTokens.remove(refreshToken);
            saveRefreshTokensToStore();
            log.info("[OAUTH] refresh rejected reason=expired_refresh_token clientId={} expiredAt={}", credentials.clientId(), state.expiresAt());
            throw new OAuthException(400, "invalid_grant");
        }

        if (!Objects.equals(state.clientId(), credentials.clientId())) {
            refreshTokens.remove(refreshToken);
            saveRefreshTokensToStore();
            log.warn("[OAUTH] refresh rejected reason=client_mismatch expected={} actual={}", state.clientId(), credentials.clientId());
            throw new OAuthException(400, "invalid_grant");
        }

        String accessToken = config.fixedAccessToken().isBlank() ? createOpaqueValue() : config.fixedAccessToken();
        int expiresIn = config.oauthAccessTokenTtlSeconds();
        accessTokens.put(accessToken, new AccessToken(state.scope(), now.plusSeconds(expiresIn)));

        String nextRefreshToken = refreshToken;
        TokenResponse tokenResponse;
        if (config.oauthRefreshTokenRotationEnabled()) {
            refreshTokens.remove(refreshToken);
            nextRefreshToken = createOpaqueValue();
            refreshTokens.put(nextRefreshToken, new RefreshTokenState(
                    credentials.clientId(),
                    state.scope(),
                    now.plusSeconds(config.oauthRefreshTokenTtlSeconds())
            ));
            saveRefreshTokensToStore();

            tokenResponse = new TokenResponse(accessToken, "Bearer", expiresIn, nextRefreshToken, state.scope());
            refreshReplayCache.put(refreshToken, new RefreshReplayCacheEntry(
                    tokenResponse,
                    credentials.clientId(),
                    now.plusSeconds(REPLAY_CACHE_TTL_SECONDS)
            ));
            return tokenResponse;
        }

        return new TokenResponse(accessToken, "Bearer", expiresIn, nextRefreshToken, state.scope());
    }

    private TokenResponse tryReplayRefreshToken(ClientCredentials credentials, String refreshToken) {
        RefreshReplayCacheEntry entry = refreshReplayCache.get(refreshToken);
        if (entry == null || entry.expiresAt().isBefore(Instant.now(clock))) {
            log.info("[OAUTH] refresh_token replay miss token={} clientId={}", shortValue(refreshToken), credentials.clientId());
            throw new OAuthException(400, "invalid_grant");
        }

        if (!Objects.equals(entry.clientId(), credentials.clientId())) {
            log.warn("[OAUTH] refresh_token replay rejected reason=client_mismatch token={} expectedClientId={} actualClientId={}",
                    shortValue(refreshToken),
                    entry.clientId(),
                    credentials.clientId());
            throw new OAuthException(400, "invalid_grant");
        }

        log.info("[OAUTH] refresh_token replay hit token={} clientId={}", shortValue(refreshToken), credentials.clientId());
        return entry.tokenResponse();
    }

    private void cleanupExpiredRefreshReplayCache() {
        Instant now = Instant.now(clock);
        refreshReplayCache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    public BearerAuthResult checkBearerAuth(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return BearerAuthResult.MISSING_TOKEN;
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (!config.fixedAccessToken().isBlank() && Objects.equals(token, config.fixedAccessToken())) {
            return BearerAuthResult.OK;
        }

        AccessToken accessToken = accessTokens.get(token);
        if (accessToken == null) {
            return BearerAuthResult.INVALID_TOKEN;
        }

        if (accessToken.expiresAt().isBefore(Instant.now(clock))) {
            accessTokens.remove(token);
            return BearerAuthResult.EXPIRED_TOKEN;
        }

        return BearerAuthResult.OK;
    }

    public boolean isBearerAuthorized(String authorizationHeader) {
        return checkBearerAuth(authorizationHeader) == BearerAuthResult.OK;
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

            Instant now = Instant.now(clock);
            int loaded = 0;
            int expiredRemoved = 0;

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

                loaded++;
                if (expiresAt.isBefore(now)) {
                    expiredRemoved++;
                    log.info("[OAUTH] refresh token expired on load clientId={} expiredAt={}", entry.clientId(), expiresAt);
                    continue;
                }

                refreshTokens.put(entry.token(), new RefreshTokenState(entry.clientId(), entry.scope(), expiresAt));
            }

            int active = refreshTokens.size();
            log.info("[OAUTH] Refresh token store loaded={} expired_removed={} active={} from {}",
                    loaded, expiredRemoved, active, storePath);

            if (expiredRemoved > 0) {
                saveRefreshTokensToStore();
            }
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

    private record ReplayCacheEntry(TokenResponse tokenResponse,
                                    String clientId,
                                    String redirectUri,
                                    String codeVerifier,
                                    Instant expiresAt) {
    }

    private record RefreshReplayCacheEntry(TokenResponse tokenResponse,
                                           String clientId,
                                           Instant expiresAt) {
    }

    private record RefreshTokenStoreDocument(int version, List<RefreshTokenEntry> tokens) {
    }

    private record RefreshTokenEntry(String token, String clientId, String scope, String expiresAt) {
    }
}
