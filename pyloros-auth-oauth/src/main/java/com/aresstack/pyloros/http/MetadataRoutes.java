package com.aresstack.pyloros.http;

import com.aresstack.pyloros.oauth.OAuthConfig;
import io.vertx.ext.web.Router;

import java.util.Map;

public final class MetadataRoutes {

    private final OAuthConfig config;

    public MetadataRoutes(OAuthConfig config) {
        this.config = config;
    }

    public void mount(Router router) {
        router.get("/.well-known/oauth-protected-resource")
                .handler(this::protectedResourceMetadata);
        router.get("/.well-known/oauth-authorization-server")
                .handler(this::authorizationServerMetadata);
        router.get("/.well-known/openid-configuration")
                .handler(this::authorizationServerMetadata);
    }

    private void protectedResourceMetadata(io.vertx.ext.web.RoutingContext context) {
        HttpJson.send(context, 200, Map.of(
                "resource", config.publicOrigin() + "/",
                "authorization_servers", new String[]{config.publicOrigin()},
                "scopes_supported", new String[]{"mcp"},
                "bearer_methods_supported", new String[]{"header"}
        ));
    }

    private void authorizationServerMetadata(io.vertx.ext.web.RoutingContext context) {
        HttpJson.send(context, 200, Map.of(
                "issuer", config.publicOrigin(),
                "authorization_endpoint", config.publicOrigin() + "/oauth/authorize",
                "token_endpoint", config.publicOrigin() + "/oauth/token",
                "response_types_supported", new String[]{"code"},
                "grant_types_supported", new String[]{"authorization_code", "refresh_token"},
                "token_endpoint_auth_methods_supported", new String[]{"client_secret_basic", "client_secret_post"},
                "code_challenge_methods_supported", new String[]{"S256"},
                "scopes_supported", new String[]{"mcp"}
        ));
    }
}
