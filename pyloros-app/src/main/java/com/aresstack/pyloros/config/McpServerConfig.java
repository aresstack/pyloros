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
        Map<String, String> headers
) {
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
