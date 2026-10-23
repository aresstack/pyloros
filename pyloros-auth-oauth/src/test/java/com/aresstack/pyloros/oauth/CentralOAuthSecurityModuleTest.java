package com.aresstack.pyloros.oauth;

import com.aresstack.anauthentia.jwt.JwtAccessTokenIssuer;
import com.aresstack.anauthentia.jwt.JwtAccessTokenValidator;
import com.aresstack.anauthentia.jwt.JwtKeyRing;
import com.aresstack.pyloros.security.AuthenticationRequest;
import com.aresstack.pyloros.security.AuthenticationResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CENTRAL mode: only offline-verifiable JWTs of the central issuer pass; there
 * is no local token state and no fixed-token backdoor in this mode.
 */
class CentralOAuthSecurityModuleTest {

    /** The service scope the gateway demands by default. */
    private static final String SCOPE = "pyloros";
    private static final String ISSUER = "https://pyloros.example.com";
    private static final String AUDIENCE = ISSUER + "/";

    private final JwtKeyRing keyRing = JwtKeyRing.generate("test-key");
    private final JwtAccessTokenIssuer tokenIssuer =
            new JwtAccessTokenIssuer(ISSUER, AUDIENCE, keyRing);
    private final CentralJwtRequestAuthenticator authenticator =
            new CentralJwtRequestAuthenticator(
                    new JwtAccessTokenValidator(ISSUER, AUDIENCE, keyRing.publicJwkSet()));

    private AuthenticationResult authenticate(String authorizationHeader) {
        Map<String, String> headers = authorizationHeader == null
                ? Map.of() : Map.of("Authorization", authorizationHeader);
        return authenticator.authenticate(new AuthenticationRequest("POST", "/", headers));
    }

    @Test
    void validCentralJwtAuthenticatesWithItsClientId() {
        Instant now = Instant.now();
        String token = tokenIssuer.generate("chatgpt", SCOPE, now, now.plus(Duration.ofMinutes(5)));
        AuthenticationResult result = authenticate("Bearer " + token);
        assertTrue(result.isAuthenticated());
    }

    @Test
    void missingWrongScopeExpiredAndForeignTokensAreRejectedWith401() {
        assertRejected(authenticate(null));
        assertRejected(authenticate("Bearer not-a-jwt"));

        Instant now = Instant.now();
        assertRejected(authenticate("Bearer " + tokenIssuer.generate(
                "chatgpt", "other-scope", now, now.plus(Duration.ofMinutes(5)))));

        Instant past = now.minus(Duration.ofHours(1));
        assertRejected(authenticate("Bearer " + tokenIssuer.generate(
                "chatgpt", SCOPE, past, past.plus(Duration.ofMinutes(5)))));

        JwtKeyRing foreign = JwtKeyRing.generate("foreign");
        String foreignToken = new JwtAccessTokenIssuer(ISSUER, AUDIENCE, foreign)
                .generate("chatgpt", SCOPE, now, now.plus(Duration.ofMinutes(5)));
        assertRejected(authenticate("Bearer " + foreignToken));
    }

    @Test
    void aFixedStaticTokenValueNeverAuthenticates() {
        // Die Fixed-Token-Hintertür des Lokalmodus existiert hier nicht.
        assertRejected(authenticate("Bearer some-fixed-access-token"));
    }

    private static void assertRejected(AuthenticationResult result) {
        assertFalse(result.isAuthenticated());
        assertEquals(401, result.statusCode());
        assertTrue(result.responseHeaders().containsKey("WWW-Authenticate"));
    }
}
