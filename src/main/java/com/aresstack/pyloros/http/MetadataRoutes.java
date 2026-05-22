package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.PylorosConfig;
import io.vertx.ext.web.Router;

import java.util.Map;

public final class MetadataRoutes {

    private final PylorosConfig config;

    public MetadataRoutes(PylorosConfig config) {
        this.config = config;
    }

    public void mount(Router router) {
        router.get("/").handler(context -> HttpJson.send(context, 200, Map.of(
                "status", "ok",
                "name", "pyloros",
                "mcp", config.publicOrigin() + config.mcpPublicPath(),
                "authorization_endpoint", config.publicOrigin() + "/oauth/authorize",
                "token_endpoint", config.publicOrigin() + "/oauth/token",
                "advertised_pkce_method", "S256",
                "pkce_compatibility_mode", true
        )));

        router.get("/health").handler(context -> HttpJson.send(context, 200, Map.of("status", "ok")));

        router.get("/.well-known/oauth-protected-resource").handler(context -> protectedResourceMetadata(context));
        router.get("/.well-known/oauth-protected-resource/sse").handler(context -> protectedResourceMetadata(context));

        router.get("/.well-known/oauth-authorization-server").handler(context -> authorizationServerMetadata(context));
        router.get("/.well-known/openid-configuration").handler(context -> authorizationServerMetadata(context));
        router.get("/.well-known/oauth-authorization-server/sse").handler(context -> authorizationServerMetadata(context));
        router.get("/sse/.well-known/oauth-authorization-server").handler(context -> authorizationServerMetadata(context));
        router.get("/.well-known/openid-configuration/sse").handler(context -> authorizationServerMetadata(context));
        router.get("/sse/.well-known/openid-configuration").handler(context -> authorizationServerMetadata(context));
    }

    private void protectedResourceMetadata(io.vertx.ext.web.RoutingContext context) {
        HttpJson.send(context, 200, Map.of(
                "resource", config.publicOrigin() + config.mcpPublicPath(),
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
                "grant_types_supported", new String[]{"authorization_code"},
                "token_endpoint_auth_methods_supported", new String[]{"client_secret_basic", "client_secret_post"},
                "code_challenge_methods_supported", new String[]{"S256"},
                "scopes_supported", new String[]{"mcp"}
        ));
    }
}
