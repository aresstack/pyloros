package com.aresstack.pyloros.langchain;

/**
 * Thrown when Ollama model creation or communication fails.
 * Messages never contain secrets.
 */
public class OllamaModelException extends RuntimeException {

    public OllamaModelException(String message) {
        super(message);
    }

    public OllamaModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
