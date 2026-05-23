package com.aresstack.pyloros.security;
    
    import io.vertx.ext.web.Router;
    
    public final class NoSecurityModule implements SecurityModule {
    
        private final RequestAuthenticator authenticator = new AllowAllRequestAuthenticator();
    
        @Override
        public RequestAuthenticator authenticator() {
            return authenticator;
        }
    
        @Override
        public void mountRoutes(Router router) {
            // Mount no security-specific routes.
        }
    }
    