package com.aresstack.pyloros.oauth;

import com.aresstack.anauthentia.jwt.JwtAccessToken;
import com.aresstack.anauthentia.jwt.JwtAccessTokenValidator;
import com.aresstack.pyloros.security.AuthenticationRequest;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.security.RequestAuthenticator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * CENTRAL mode: validates RS256 access tokens of the central AresStack issuer
 * offline (signature via cached JWKS, issuer, audience, expiry, mcp scope).
 * No local token state exists in this mode — and no fixed-token backdoor.
 */
public final class CentralJwtRequestAuthenticator implements RequestAuthenticator {

    static final String DEFAULT_REQUIRED_SCOPE = "mcp";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WWW_AUTHENTICATE_INVALID_TOKEN =
            "Bearer error=\"invalid_token\", error_description=\"The access token is invalid or expired\"";

    private final JwtAccessTokenValidator validator;
    private final String requiredScope;

    public CentralJwtRequestAuthenticator(JwtAccessTokenValidator validator) {
        this(validator, DEFAULT_REQUIRED_SCOPE);
    }

    public CentralJwtRequestAuthenticator(JwtAccessTokenValidator validator, String requiredScope) {
        this.validator = validator;
        this.requiredScope = requiredScope == null || requiredScope.isBlank()
                ? DEFAULT_REQUIRED_SCOPE : requiredScope.trim();
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        String header = request.header("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return AuthenticationResult.rejected(401, "missing_token", rejectionHeaders());
        }
        Optional<JwtAccessToken> validated =
                validator.validate(header.substring(BEARER_PREFIX.length()).trim());
        if (validated.isEmpty() || !validated.get().hasScope(requiredScope)) {
            return AuthenticationResult.rejected(401, "invalid_token", rejectionHeaders());
        }
        // The granted scopes travel on, so a protected call can demand more than the
        // gate does.
        return AuthenticationResult.authenticated(validated.get().getClientId(),
                java.util.List.copyOf(validated.get().getScopes()));
    }

    private static Map<String, String> rejectionHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("WWW-Authenticate", WWW_AUTHENTICATE_INVALID_TOKEN);
        return headers;
    }
}
