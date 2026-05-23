package com.aresstack.pyloros.oauth;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.domain.oauth.ClientCredentials;
import com.aresstack.pyloros.domain.oauth.OAuthException;
import com.aresstack.pyloros.domain.oauth.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthServiceRefreshReplayTest {

    private static final ClientCredentials CREDENTIALS = new ClientCredentials("angel", "change-me");
    private static final String REDIRECT_URI = "https://chat.openai.com/a";

    @TempDir
    Path tempDir;

    @Test
    void refreshRotationDisabledKeepsRefreshTokenReusable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        OAuthService service = new OAuthService(config(false, tempDir.resolve("tokens-disabled.json")), clock);

        String authorizationCode = issueAuthorizationCode(service);
        TokenResponse initial = service.exchangeAuthorizationCode(
                CREDENTIALS,
                "authorization_code",
                authorizationCode,
                null,
                REDIRECT_URI,
                null
        );

        TokenResponse refresh1 = service.exchangeAuthorizationCode(
                CREDENTIALS,
                "refresh_token",
                null,
                initial.refreshToken(),
                null,
                null
        );
        TokenResponse refresh2 = service.exchangeAuthorizationCode(
                CREDENTIALS,
                "refresh_token",
                null,
                initial.refreshToken(),
                null,
                null
        );

        assertEquals(initial.refreshToken(), refresh1.refreshToken());
        assertEquals(initial.refreshToken(), refresh2.refreshToken());
        assertNotNull(refresh1.accessToken());
        assertNotNull(refresh2.accessToken());
    }

    @Test
    void refreshRotationEnabledReplaysDuplicateWithinTenSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        OAuthService service = new OAuthService(config(true, tempDir.resolve("tokens-enabled.json")), clock);

        String authorizationCode = issueAuthorizationCode(service);
        TokenResponse initial = service.exchangeAuthorizationCode(
                CREDENTIALS,
                "authorization_code",
                authorizationCode,
                null,
                REDIRECT_URI,
                null
        );

        TokenResponse refresh1 = service.exchangeAuthorizationCode(
                CREDENTIALS,
                "refresh_token",
                null,
                initial.refreshToken(),
                null,
                null
        );
        TokenResponse refresh2 = service.exchangeAuthorizationCode(
                CREDENTIALS,
                "refresh_token",
                null,
                initial.refreshToken(),
                null,
                null
        );

        assertNotEquals(initial.refreshToken(), refresh1.refreshToken());
        assertEquals(refresh1, refresh2);

        clock.plusSeconds(11);

        OAuthException exception = assertThrows(OAuthException.class, () -> service.exchangeAuthorizationCode(
                CREDENTIALS,
                "refresh_token",
                null,
                initial.refreshToken(),
                null,
                null
        ));
        assertEquals(400, exception.statusCode());
        assertEquals("invalid_grant", exception.error());
    }

    private String issueAuthorizationCode(OAuthService service) {
        String location = service.authorize(
                "code",
                CREDENTIALS.clientId(),
                REDIRECT_URI,
                "state",
                "mcp",
                null,
                null
        );

        String query = URI.create(location).getQuery();
        for (String pair : query.split("&")) {
            if (pair.startsWith("code=")) {
                return pair.substring("code=".length());
            }
        }
        throw new AssertionError("Authorization code missing from redirect");
    }

    private static ServerConfig config(boolean rotationEnabled, Path storePath) {
        return new TestServerConfig(
                "https://current-car.com",
                "/sse",
                "2025-03-26",
                CREDENTIALS.clientId(),
                CREDENTIALS.clientSecret(),
                "",
                3600,
                2592000,
                rotationEnabled,
                storePath.toString()
        );
    }

    private record TestServerConfig(
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
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void plusSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

