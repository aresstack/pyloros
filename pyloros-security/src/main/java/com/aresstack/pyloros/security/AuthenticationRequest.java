package com.aresstack.pyloros.security;
    
    import java.util.Collections;
    import java.util.LinkedHashMap;
    import java.util.Locale;
    import java.util.Map;
    
    public final class AuthenticationRequest {
    
        private final String method;
        private final String path;
        private final Map<String, String> headers;
    
        public AuthenticationRequest(String method, String path, Map<String, String> headers) {
            this.method = method == null ? "" : method;
            this.path = path == null ? "" : path;
            this.headers = normalizeHeaders(headers);
        }
    
        public String method() {
            return method;
        }
    
        public String path() {
            return path;
        }
    
        public String header(String name) {
            if (name == null) {
                return null;
            }
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    
        public Map<String, String> headers() {
            return headers;
        }
    
        private static Map<String, String> normalizeHeaders(Map<String, String> source) {
            Map<String, String> normalized = new LinkedHashMap<>();
            if (source != null) {
                for (Map.Entry<String, String> entry : source.entrySet()) {
                    if (entry.getKey() != null) {
                        normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
                    }
                }
            }
            return Collections.unmodifiableMap(normalized);
        }
    }
    