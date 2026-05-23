package com.aresstack.pyloros.security;
    
    public final class AllowAllRequestAuthenticator implements RequestAuthenticator {
    
        @Override
        public AuthenticationResult authenticate(AuthenticationRequest request) {
            return AuthenticationResult.authenticated("anonymous");
        }
    }
    