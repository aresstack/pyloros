package com.aresstack.pyloros.oauth;
    
    public interface OAuthConfig {
    
        String publicOrigin();
    
        String mcpPublicPath();
    
        String oauthClientId();
    
        String oauthClientSecret();
    
        String fixedAccessToken();
    
        int oauthAccessTokenTtlSeconds();
    
        int oauthRefreshTokenTtlSeconds();
    
        boolean oauthRefreshTokenRotationEnabled();
    
        String oauthRefreshTokenStorePath();
    }
    