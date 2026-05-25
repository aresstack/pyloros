package com.aresstack.pyloros.langchain;

import java.util.Objects;

/**
 * Record describing a single tool invocation performed during a LangChain run.
 *
 * <p>Only non-sensitive fields are kept: the fully-qualified tool name, a hash of the
 * arguments (never the raw arguments), the duration of the call and the resulting state.
 */
public record LangChainToolCall(
        String toolName,
        String argumentsHash,
        long durationMs,
        String resultState
) {

    public LangChainToolCall {
        toolName = requireText(toolName, "toolName");
        argumentsHash = requireText(argumentsHash, "argumentsHash");
        resultState = requireText(resultState, "resultState");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }
}
