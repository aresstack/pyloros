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
        /** Scopes the token actually carries; empty for a rejection. */
        private final java.util.List<String> scopes;
    
        private AuthenticationResult(boolean authenticated,
                                     String subject,
                                     int statusCode,
                                     String errorCode,
                                     Map<String, String> responseHeaders,
                                     java.util.List<String> scopes) {
            this.authenticated = authenticated;
            this.subject = subject == null ? "" : subject;
            this.statusCode = statusCode;
            this.errorCode = errorCode == null ? "" : errorCode;
            this.responseHeaders = copy(responseHeaders);
            this.scopes = scopes == null ? Collections.emptyList()
                    : java.util.List.copyOf(scopes);
        }
    
        public static AuthenticationResult authenticated(String subject) {
            return authenticated(subject, Collections.emptyList());
        }

        /**
         * Authenticated together with the granted scopes.
         *
         * <p>Carrying them past the gate is what makes a per-tool permission
         * check possible at all: the scope set used to be validated once and
         * then discarded, so nothing downstream could tell read from write.
         */
        public static AuthenticationResult authenticated(String subject,
                                                        java.util.List<String> scopes) {
            return new AuthenticationResult(true, subject, 200, "",
                    Collections.emptyMap(), scopes);
        }
    
        public static AuthenticationResult rejected(int statusCode, String errorCode,
                                                    Map<String, String> responseHeaders) {
            return new AuthenticationResult(false, "", statusCode, errorCode,
                    responseHeaders, Collections.emptyList());
        }

        /** The scopes the validated token carries. */
        public java.util.List<String> scopes() {
            return scopes;
        }

        public boolean hasScope(String required) {
            return required == null || required.isBlank() || scopes.contains(required);
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
    