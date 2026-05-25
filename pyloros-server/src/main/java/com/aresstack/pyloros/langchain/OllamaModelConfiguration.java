package com.aresstack.pyloros.langchain;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for an Ollama model connection.
 */
public record OllamaModelConfiguration(
        String baseUrl,
        String modelName,
        Double temperature,
        Duration timeout
) {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL_NAME = "qwen2.5-coder:7b";
    public static final double DEFAULT_TEMPERATURE = 0.1;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    public OllamaModelConfiguration {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    /**
     * Creates a configuration with all defaults.
     */
    public static OllamaModelConfiguration defaults() {
        return new OllamaModelConfiguration(
                DEFAULT_BASE_URL,
                DEFAULT_MODEL_NAME,
                DEFAULT_TEMPERATURE,
                DEFAULT_TIMEOUT
        );
    }
}
