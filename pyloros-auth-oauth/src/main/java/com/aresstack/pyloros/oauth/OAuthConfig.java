package com.aresstack.pyloros.oauth;
    
    public interface OAuthConfig {
    
        String publicOrigin();
    
            String oauthClientId();
    
        String oauthClientSecret();
    
        String fixedAccessToken();
    
        int oauthAccessTokenTtlSeconds();
    
        int oauthRefreshTokenTtlSeconds();
    
        boolean oauthRefreshTokenRotationEnabled();
    
        String oauthRefreshTokenStorePath();

        /** Non-empty = CENTRAL mode: this AresStack issuer signs the access tokens. */
        default String centralOauthIssuer() {
            return "";
        }
    }
    