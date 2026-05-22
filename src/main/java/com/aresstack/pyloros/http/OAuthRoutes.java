package com.aresstack.pyloros.http;

import com.aresstack.pyloros.domain.oauth.ClientCredentials;
import com.aresstack.pyloros.domain.oauth.OAuthException;
import com.aresstack.pyloros.domain.oauth.TokenResponse;
import com.aresstack.pyloros.oauth.OAuthService;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthRoutes {

    private static final Logger log = LoggerFactory.getLogger(OAuthRoutes.class);

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
            sendOAuthError(context, null, exception);
        }
    }

    private void token(RoutingContext context) {
        String grantType = context.request().getFormAttribute("grant_type");
        try {
            ClientCredentials credentials = readClientCredentials(context);
            TokenResponse tokenResponse = oauthService.exchangeAuthorizationCode(
                    credentials,
                    grantType,
                    context.request().getFormAttribute("code"),
                    context.request().getFormAttribute("refresh_token"),
                    context.request().getFormAttribute("redirect_uri"),
                    context.request().getFormAttribute("code_verifier")
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", tokenResponse.accessToken());
            body.put("token_type", tokenResponse.tokenType());
            body.put("expires_in", tokenResponse.expiresIn());
            if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
                body.put("refresh_token", tokenResponse.refreshToken());
            }
            body.put("scope", tokenResponse.scope());

            log.info("[OAUTH] token response status=200 grant_type={} has_access_token={} has_refresh_token={} token_type={} expires_in={} scope={}",
                    grantType,
                    body.containsKey("access_token"),
                    body.containsKey("refresh_token"),
                    tokenResponse.tokenType(),
                    tokenResponse.expiresIn(),
                    tokenResponse.scope());

            sendTokenResponse(context, 200, body);
        } catch (OAuthException exception) {
            sendOAuthError(context, grantType, exception);
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

    private void sendTokenResponse(RoutingContext context, int statusCode, Map<String, Object> body) {
        try {
            context.response()
                    .setStatusCode(statusCode)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader("Pragma", "no-cache")
                    .end(HttpJson.mapper().writeValueAsString(body));
        } catch (Exception exception) {
            context.fail(exception);
        }
    }

    private void sendOAuthError(RoutingContext context, String grantType, OAuthException exception) {
        log.info("[OAUTH] token response status={} grant_type={} error={} error_description={}",
                exception.statusCode(), grantType, exception.error(), exception.errorDescription());
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
