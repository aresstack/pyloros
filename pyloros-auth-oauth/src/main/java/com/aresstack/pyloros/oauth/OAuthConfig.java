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
}
