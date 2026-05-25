package com.aresstack.pyloros.langchain;

/** Thrown when the underlying model backend (Ollama, ...) fails or is unavailable. */
public final class ModelExecutionException extends LangChainException {

    public ModelExecutionException(String message) {
        super(LangChainStopReason.MODEL_ERROR, message);
    }

    public ModelExecutionException(String message, Throwable cause) {
        super(LangChainStopReason.MODEL_ERROR, message, cause);
    }
}
