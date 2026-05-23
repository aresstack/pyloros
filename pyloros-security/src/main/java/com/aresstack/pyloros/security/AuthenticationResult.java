package com.aresstack.pyloros.security;
    
    import java.util.Collections;
    import java.util.LinkedHashMap;
    import java.util.Map;
    
    public final class AuthenticationResult {
    
        private final boolean authenticated;
        private final String subject;
        private final int statusCode;
        private final String errorCode;
        private final Map<String, String> responseHeaders;
    
        private AuthenticationResult(boolean authenticated,
                                     String subject,
                                     int statusCode,
                                     String errorCode,
                                     Map<String, String> responseHeaders) {
            this.authenticated = authenticated;
            this.subject = subject == null ? "" : subject;
            this.statusCode = statusCode;
            this.errorCode = errorCode == null ? "" : errorCode;
            this.responseHeaders = copy(responseHeaders);
        }
    
        public static AuthenticationResult authenticated(String subject) {
            return new AuthenticationResult(true, subject, 200, "", Collections.emptyMap());
        }
    
        public static AuthenticationResult rejected(int statusCode, String errorCode, Map<String, String> responseHeaders) {
            return new AuthenticationResult(false, "", statusCode, errorCode, responseHeaders);
        }
    
        public boolean isAuthenticated() {
            return authenticated;
        }
    
        public String subject() {
            return subject;
        }
    
        public int statusCode() {
            return statusCode;
        }
    
        public String errorCode() {
            return errorCode;
        }
    
        public Map<String, String> responseHeaders() {
            return responseHeaders;
        }
    
        private static Map<String, String> copy(Map<String, String> source) {
            if (source == null || source.isEmpty()) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
    