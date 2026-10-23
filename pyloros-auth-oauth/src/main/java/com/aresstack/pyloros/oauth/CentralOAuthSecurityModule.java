package com.aresstack.pyloros.oauth;

import com.aresstack.anauthentia.jwt.JwtAccessTokenValidator;
import com.aresstack.pyloros.http.HttpJson;
import com.aresstack.pyloros.security.RequestAuthenticator;
import com.aresstack.pyloros.security.SecurityModule;
import io.vertx.ext.web.Router;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * CENTRAL mode (security.mode=central-oauth): the AresStack webserver is the
 * authorization server; Pyloros only validates its JWTs offline. This process
 * mounts NO /oauth/* routes and no authorization-server metadata — the reverse
 * proxy serves those from the central runtime. Only the protected-resource
 * metadata stay here and point at the central issuer (RFC 9728).
 */
public final class CentralOAuthSecurityModule implements SecurityModule {

    private final OAuthConfig config;
    private final RequestAuthenticator authenticator;

    public CentralOAuthSecurityModule(OAuthConfig config) {
        this(config, new CentralJwtRequestAuthenticator(buildValidator(config),
                config.requiredScope()));
    }

    /** Test seam: inject an authenticator with a known key set. */
    CentralOAuthSecurityModule(OAuthConfig config, RequestAuthenticator authenticator) {
        this.config = config;
        this.authenticator = authenticator;
    }

    private static JwtAccessTokenValidator buildValidator(OAuthConfig config) {
        String issuer = config.centralOauthIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "security.mode=central-oauth requires oauth.central-issuer / CENTRAL_OAUTH_ISSUER");
        }
        try {
            return JwtAccessTokenValidator.forRemoteJwks(issuer, issuer + "/",
                    java.net.URI.create(issuer + "/.well-known/jwks.json").toURL());
        } catch (MalformedURLException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid central issuer URL: " + issuer, invalid);
        }
    }

    @Override
    public RequestAuthenticator authenticator() {
        return authenticator;
    }

    @Override
    public void mountRoutes(Router router) {
        router.get("/.well-known/oauth-protected-resource").handler(context ->
                HttpJson.send(context, 200, Map.of(
                        "resource", config.publicOrigin() + "/",
                        "authorization_servers", new String[]{config.centralOauthIssuer()},
                        "scopes_supported", new String[]{config.requiredScope()},
                        "bearer_methods_supported", new String[]{"header"})));
    }
}
