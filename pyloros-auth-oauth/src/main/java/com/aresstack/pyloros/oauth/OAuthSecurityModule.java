package com.aresstack.pyloros.oauth;
    
    import com.aresstack.pyloros.http.MetadataRoutes;
    import com.aresstack.pyloros.http.OAuthRoutes;
    import com.aresstack.pyloros.security.RequestAuthenticator;
    import com.aresstack.pyloros.security.SecurityModule;
    import io.vertx.ext.web.Router;
    
    public final class OAuthSecurityModule implements SecurityModule {
    
        private final OAuthService oauthService;
        private final RequestAuthenticator authenticator;
        private final OAuthConfig config;
    
        public OAuthSecurityModule(OAuthConfig config) {
            this.oauthService = new OAuthService(config);
            this.authenticator = new OAuthRequestAuthenticator(oauthService);
            this.config = config;
        }
    
        @Override
        public RequestAuthenticator authenticator() {
            return authenticator;
        }
    
        @Override
        public void mountRoutes(Router router) {
            new MetadataRoutes(config).mount(router);
            new OAuthRoutes(oauthService).mount(router);
        }
    }
    