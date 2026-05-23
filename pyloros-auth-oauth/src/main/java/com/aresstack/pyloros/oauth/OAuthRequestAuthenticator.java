package com.aresstack.pyloros.oauth;
    
    import com.aresstack.pyloros.domain.oauth.BearerAuthResult;
    import com.aresstack.pyloros.security.AuthenticationRequest;
    import com.aresstack.pyloros.security.AuthenticationResult;
    import com.aresstack.pyloros.security.RequestAuthenticator;
    
    import java.util.LinkedHashMap;
    import java.util.Map;
    
    public final class OAuthRequestAuthenticator implements RequestAuthenticator {
    
        private static final String WWW_AUTHENTICATE_INVALID_TOKEN =
                "Bearer error=\"invalid_token\", error_description=\"The access token is invalid or expired\"";
    
        private final OAuthService oauthService;
    
        public OAuthRequestAuthenticator(OAuthService oauthService) {
            this.oauthService = oauthService;
        }
    
        @Override
        public AuthenticationResult authenticate(AuthenticationRequest request) {
            BearerAuthResult result = oauthService.checkBearerAuth(request.header("Authorization"));
            if (result == BearerAuthResult.OK) {
                return AuthenticationResult.authenticated("oauth-client");
            }
            return AuthenticationResult.rejected(401, reason(result), rejectionHeaders());
        }
    
        private String reason(BearerAuthResult result) {
            if (result == BearerAuthResult.MISSING_TOKEN) {
                return "missing_token";
            }
            if (result == BearerAuthResult.EXPIRED_TOKEN) {
                return "expired_token";
            }
            if (result == BearerAuthResult.INVALID_TOKEN) {
                return "invalid_token";
            }
            return "unknown";
        }
    
        private Map<String, String> rejectionHeaders() {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("WWW-Authenticate", WWW_AUTHENTICATE_INVALID_TOKEN);
            return headers;
        }
    }
    