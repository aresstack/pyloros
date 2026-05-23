package com.aresstack.pyloros.config;

public interface ServerConfig {

    String publicOrigin();

    String mcpPublicPath();

    String mcpProtocolVersion();

    String oauthClientId();

    String oauthClientSecret();

    String fixedAccessToken();

    int oauthAccessTokenTtlSeconds();

    int oauthRefreshTokenTtlSeconds();

    boolean oauthRefreshTokenRotationEnabled();

    String oauthRefreshTokenStorePath();
}

