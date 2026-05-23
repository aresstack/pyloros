package com.aresstack.pyloros.security;
    
    public interface RequestAuthenticator {
    
        AuthenticationResult authenticate(AuthenticationRequest request);
    }
    