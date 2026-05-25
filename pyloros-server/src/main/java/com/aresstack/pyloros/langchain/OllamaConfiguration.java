package com.aresstack.pyloros.langchain;

import java.net.URI;
import java.util.Objects;

public record OllamaConfiguration(
        URI baseUrl,
        String model
) {

    public static final String DEFAULT_MODEL = "qwen2.5-coder:7b";
    public static final URI DEFAULT_BASE_URL = URI.create("http://localhost:11434");

    public OllamaConfiguration {
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : validateUrl(baseUrl);
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    public OllamaConfiguration() {
        this(DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    private static URI validateUrl(URI url) {
        Objects.requireNonNull(url, "baseUrl must not be null");
        String scheme = url.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("ollama baseUrl must use http or https scheme, got: " + url);
        }
        return url;
    }
}
