package com.aresstack.pyloros.langchain;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;

/**
 * Factory for creating LangChain4j Ollama chat models from configuration.
 * <p>
 * Error messages never include secrets or sensitive connection details beyond
 * the base URL and model name which are considered non-secret configuration.
 */
public final class OllamaModelFactory {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelFactory.class);

    private OllamaModelFactory() {
    }

    /**
     * Creates an Ollama chat model using the provided configuration.
     *
     * @param config the Ollama model configuration
     * @return a configured ChatModel instance
     * @throws OllamaModelException if the configuration is invalid
     */
    public static ChatModel create(OllamaModelConfiguration config) {
        Objects.requireNonNull(config, "config must not be null");
        validateBaseUrl(config.baseUrl());

        log.info("Creating Ollama model: baseUrl={}, model={}, timeout={}s",
                config.baseUrl(), config.modelName(), config.timeout().toSeconds());

        return OllamaChatModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .temperature(config.temperature())
                .timeout(config.timeout())
                .build();
    }

    /**
     * Creates an Ollama chat model using default configuration.
     *
     * @return a configured ChatModel instance with defaults
     */
    public static ChatModel createDefault() {
        return create(OllamaModelConfiguration.defaults());
    }

    private static void validateBaseUrl(String baseUrl) {
        try {
            var uri = URI.create(baseUrl);
            var scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new OllamaModelException(
                        "Invalid Ollama base URL: scheme must be http or https, got: " + scheme);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new OllamaModelException("Invalid Ollama base URL: host is missing");
            }
        } catch (IllegalArgumentException e) {
            throw new OllamaModelException("Invalid Ollama base URL: malformed URI");
        }
    }
}
