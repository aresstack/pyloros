package com.aresstack.pyloros.security;
    
    import io.vertx.ext.web.Router;
    
    public interface SecurityModule {
    
        RequestAuthenticator authenticator();
    
        void mountRoutes(Router router);
    }
    