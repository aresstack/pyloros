package com.aresstack.pyloros.http;

import com.aresstack.pyloros.domain.oauth.ClientCredentials;
import com.aresstack.pyloros.domain.oauth.OAuthException;
import com.aresstack.pyloros.domain.oauth.TokenResponse;
import com.aresstack.pyloros.oauth.OAuthService;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthRoutes {

    private final OAuthService oauthService;

    public OAuthRoutes(OAuthService oauthService) {
        this.oauthService = oauthService;
    }

    public void mount(Router router) {
        router.get("/oauth/authorize").handler(this::authorize);
        router.post("/oauth/token").handler(this::token);
    }

    private void authorize(RoutingContext context) {
        try {
            String location = oauthService.authorize(
                    queryParam(context, "response_type"),
                    queryParam(context, "client_id"),
                    queryParam(context, "redirect_uri"),
                    queryParam(context, "state"),
                    queryParamOrDefault(context, "scope", "mcp"),
                    queryParam(context, "code_challenge"),
                    queryParam(context, "code_challenge_method")
            );

            context.response()
                    .setStatusCode(302)
                    .putHeader(HttpHeaders.LOCATION, location)
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .end();
        } catch (OAuthException exception) {
            sendOAuthError(context, exception);
        }
    }

    private void token(RoutingContext context) {
        try {
            ClientCredentials credentials = readClientCredentials(context);
            TokenResponse tokenResponse = oauthService.exchangeAuthorizationCode(
                    credentials,
                    context.request().getFormAttribute("grant_type"),
                    context.request().getFormAttribute("code"),
                    context.request().getFormAttribute("redirect_uri"),
                    context.request().getFormAttribute("code_verifier")
            );

            HttpJson.send(context, 200, Map.of(
                    "access_token", tokenResponse.accessToken(),
                    "token_type", tokenResponse.tokenType(),
                    "expires_in", tokenResponse.expiresIn(),
                    "scope", tokenResponse.scope()
            ));
        } catch (OAuthException exception) {
            sendOAuthError(context, exception);
        }
    }

    private ClientCredentials readClientCredentials(RoutingContext context) {
        String authorization = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Basic ")) {
            String encoded = authorization.substring("Basic ".length()).trim();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separatorIndex = decoded.indexOf(':');
            if (separatorIndex >= 0) {
                return new ClientCredentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
            }
        }

        return new ClientCredentials(
                nullToEmpty(context.request().getFormAttribute("client_id")),
                nullToEmpty(context.request().getFormAttribute("client_secret"))
        );
    }

    private void sendOAuthError(RoutingContext context, OAuthException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", exception.error());
        if (exception.errorDescription() != null) {
            body.put("error_description", exception.errorDescription());
        }
        HttpJson.send(context, exception.statusCode(), body);
    }

    private String queryParam(RoutingContext context, String name) {
        return context.queryParam(name).stream().findFirst().orElse(null);
    }

    private String queryParamOrDefault(RoutingContext context, String name, String defaultValue) {
        return context.queryParam(name).stream().findFirst().orElse(defaultValue);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
