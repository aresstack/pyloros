package com.aresstack.pyloros.upstream.mcp;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record McpUpstreamConfig(
        String providerId,
        String transport,
        URI url,
        Map<String, String> headers,
        int connectTimeoutMillis,
        int responseTimeoutMillis,
        String source
) {
    public McpUpstreamConfig {
        Objects.requireNonNull(providerId, "providerId must not be null");
        if (providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        Objects.requireNonNull(transport, "transport must not be null");
        if (transport.isBlank()) {
            throw new IllegalArgumentException("transport must not be blank");
        }
        Objects.requireNonNull(url, "url must not be null");
        headers = immutableHeaders(headers);
        source = source == null ? "" : source;
    }

    private static Map<String, String> immutableHeaders(Map<String, String> headers) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    normalized.put(key, value);
                }
            });
        }
        return Collections.unmodifiableMap(normalized);
    }
}
