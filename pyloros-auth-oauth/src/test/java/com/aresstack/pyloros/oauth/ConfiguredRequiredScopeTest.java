package com.aresstack.pyloros.oauth;

import com.aresstack.anauthentia.jwt.JwtAccessTokenIssuer;
import com.aresstack.anauthentia.jwt.JwtAccessTokenValidator;
import com.aresstack.anauthentia.jwt.JwtKeyRing;
import com.aresstack.pyloros.security.AuthenticationRequest;
import com.aresstack.pyloros.security.AuthenticationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway scope comes from the configuration, and the granted scopes travel on.
 *
 * <p>It used to be the constant {@code mcp}, which made the webserver's per-site
 * scope configuration ineffective: a service-specific scope such as {@code pyloros}
 * could be issued but never demanded.
 */
class ConfiguredRequiredScopeTest {

    @Test
    void acceptsATokenCarryingTheConfiguredScope() {
        AuthenticationResult result = authenticate("pyloros", List.of("pyloros"));

        assertTrue(result.isAuthenticated());
        assertEquals("chatgpt", result.subject());
    }

    @Test
    void rejectsATokenWithoutTheConfiguredScope() {
        // The generic mcp scope is no longer enough: the service demands its own.
        AuthenticationResult result = authenticate("pyloros", List.of("mcp"));

        assertFalse(result.isAuthenticated());
        assertEquals(401, result.statusCode());
        assertEquals("invalid_token", result.errorCode());
    }

    @Test
    void defaultsToTheServiceScopeSoANeighbourTokenIsNotAccepted() {
        // Unset or blank configuration must not fall back to a scope that another
        // service on the same authorization server also issues.
        assertTrue(authenticate(null, List.of("pyloros")).isAuthenticated());
        assertTrue(authenticate("   ", List.of("pyloros")).isAuthenticated());
        assertFalse(authenticate(null, List.of("mcp")).isAuthenticated());
        assertFalse(authenticate(null, List.of("askai")).isAuthenticated(),
                "a token minted for the neighbouring service must be rejected");
    }

    @Test
    void theGrantedScopesAreAvailableForAPerToolCheck() {
        AuthenticationResult result =
                authenticate("pyloros", List.of("pyloros", "pyloros:read"));

        assertEquals(List.of("pyloros", "pyloros:read"), result.scopes());
        assertTrue(result.hasScope("pyloros:read"));
        assertFalse(result.hasScope("pyloros:write"),
                "a scope that was not granted must not be reported as held");
    }

    @Test
    void aRejectionCarriesNoScopes() {
        assertTrue(authenticate("pyloros", List.of("mcp")).scopes().isEmpty());
    }

    @Test
    void aMissingOrMalformedHeaderIsRejectedBeforeAnyScopeCheck() {
        CentralJwtRequestAuthenticator authenticator =
                new CentralJwtRequestAuthenticator(validator(), "pyloros");

        AuthenticationResult missing = authenticator.authenticate(
                new AuthenticationRequest("POST", "/", Map.of()));
        assertFalse(missing.isAuthenticated());
        assertEquals("missing_token", missing.errorCode());

        AuthenticationResult wrongScheme = authenticator.authenticate(
                new AuthenticationRequest("POST", "/", Map.of("Authorization", "Basic abc")));
        assertEquals("missing_token", wrongScheme.errorCode());
    }

    private static final String ISSUER = "https://pyloros.example.org";
    private static final String AUDIENCE = ISSUER + "/";
    private static final JwtKeyRing KEYS = JwtKeyRing.generate("test-key");

    private AuthenticationResult authenticate(String requiredScope, List<String> tokenScopes) {
        // A real signed token against a real validator, so the signature, issuer and
        // audience path is exercised alongside the scope decision.
        String token = new JwtAccessTokenIssuer(ISSUER, AUDIENCE, KEYS).generate(
                "chatgpt", String.join(" ", tokenScopes), null,
                Instant.now(), Instant.now().plusSeconds(60));
        return new CentralJwtRequestAuthenticator(validator(), requiredScope)
                .authenticate(new AuthenticationRequest("POST", "/",
                        Map.of("Authorization", "Bearer " + token)));
    }

    private static JwtAccessTokenValidator validator() {
        return new JwtAccessTokenValidator(ISSUER, AUDIENCE, KEYS.publicJwkSet());
    }
}
