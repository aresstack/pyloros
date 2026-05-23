package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.ServerConfig;
import io.vertx.ext.web.Router;

import java.util.Map;

public final class MetadataRoutes {

    private static final String LEGACY_MCP_PUBLIC_PATH = "/sse";

    private final ServerConfig config;

    public MetadataRoutes(ServerConfig config) {
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


        router.get("/.well-known/oauth-protected-resource").handler(context -> protectedResourceMetadata(context, config.mcpPublicPath()));
        mountProtectedResourceAlias(router, config.mcpPublicPath());
        if (!LEGACY_MCP_PUBLIC_PATH.equals(config.mcpPublicPath())) {
            mountProtectedResourceAlias(router, LEGACY_MCP_PUBLIC_PATH);
        }

        router.get("/.well-known/oauth-authorization-server").handler(this::authorizationServerMetadata);
        router.get("/.well-known/openid-configuration").handler(this::authorizationServerMetadata);
        mountAuthorizationMetadataAliases(router, config.mcpPublicPath());
        if (!LEGACY_MCP_PUBLIC_PATH.equals(config.mcpPublicPath())) {
            mountAuthorizationMetadataAliases(router, LEGACY_MCP_PUBLIC_PATH);
        }
    }

    private void mountProtectedResourceAlias(Router router, String publicPath) {
        String segment = endpointSegment(publicPath);
        router.get("/.well-known/oauth-protected-resource/" + segment)
                .handler(context -> protectedResourceMetadata(context, publicPath));
    }

    private void mountAuthorizationMetadataAliases(Router router, String publicPath) {
        String segment = endpointSegment(publicPath);
        router.get("/.well-known/oauth-authorization-server/" + segment).handler(this::authorizationServerMetadata);
        router.get(publicPath + "/.well-known/oauth-authorization-server").handler(this::authorizationServerMetadata);
        router.get("/.well-known/openid-configuration/" + segment).handler(this::authorizationServerMetadata);
        router.get(publicPath + "/.well-known/openid-configuration").handler(this::authorizationServerMetadata);
    }

    private static String endpointSegment(String publicPath) {
        return publicPath.startsWith("/") ? publicPath.substring(1) : publicPath;
    }

    private void protectedResourceMetadata(io.vertx.ext.web.RoutingContext context, String publicPath) {
        HttpJson.send(context, 200, Map.of(
                "resource", config.publicOrigin() + publicPath,
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
