package com.aresstack.pyloros.oauth;

/**
 * Configuration of the central-OAuth resource server. Pyloros no longer runs
 * its own authorization server: tokens are issued by the AresStack webserver
 * and validated offline against its JWKS.
 */
public interface OAuthConfig {

    /** Public https origin of THIS service, e.g. {@code https://pyloros.example.org}. */
    String publicOrigin();

    /**
     * The central issuer that signs the access tokens. In the per-site issuer
     * model this is the service's own public origin.
     */
    String centralOauthIssuer();

    /**
     * The scope an incoming token must carry for this service.
     *
     * <p>Was a hardcoded {@code mcp} in the authenticator, which made the
     * webserver's per-site scope configuration ineffective: a service-specific
     * scope could be issued but never demanded. The default is the service's own
     * name, so a token minted for a neighbouring service on the same authorization
     * server cannot be replayed here.
     */
    default String requiredScope() {
        return "pyloros";
    }
}
