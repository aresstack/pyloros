package com.aresstack.pyloros.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpServerConfig(
        String type,
        URI url,
        RequestInitConfig requestInit,
        Map<String, String> headers,
        Integer connectTimeoutMillis,
        Integer responseTimeoutMillis
) {
    /**
     * @return the configured value, or {@code fallback} when absent or not positive
     *
     * <p>Both timeouts used to be compiled-in constants, which made a long-running upstream tool
     * impossible to accommodate without a rebuild.
     */
    public int connectTimeoutMillisOr(int fallback) {
        return positiveOr(connectTimeoutMillis, fallback);
    }

    /** @see #connectTimeoutMillisOr(int) */
    public int responseTimeoutMillisOr(int fallback) {
        return positiveOr(responseTimeoutMillis, fallback);
    }

    private static int positiveOr(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    public Map<String, String> resolvedHeaders() {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    merged.put(key, value);
                }
            });
        }
        if (requestInit != null && requestInit.headers() != null) {
            requestInit.headers().forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    merged.put(key, value);
                }
            });
        }
        return Map.copyOf(merged);
    }
}
