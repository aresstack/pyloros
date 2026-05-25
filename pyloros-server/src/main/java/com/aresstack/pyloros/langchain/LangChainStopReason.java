package com.aresstack.pyloros.langchain;

import java.util.Objects;

/**
 * Reasons why a LangChain-backed {@code pyloros-ai/ask} invocation stopped.
 *
 * <p>The {@link #wireName()} values are the canonical strings that must appear in
 * MCP tool results (see {@link LangChainToolResultMapper}) and in audit/session
 * logs (see issue R5-09).
 */
public enum LangChainStopReason {

    /** The model produced a final answer and no further action is required. */
    COMPLETED("completed"),

    /** The configured {@code maxToolCalls} limit was reached. */
    MAX_TOOL_CALLS("max_tool_calls"),

    /** The configured runtime limit ({@code maxRuntimeSeconds}) elapsed. */
    TIMEOUT("timeout"),

    /** A Pyloros policy denied a requested tool invocation. */
    POLICY_DENIED("policy_denied"),

    /** A selected tool failed during execution. */
    TOOL_ERROR("tool_error"),

    /** The underlying model backend (e.g. Ollama) failed or was unavailable. */
    MODEL_ERROR("model_error"),

    /** The run was cancelled (e.g. via a client cancellation request). */
    CANCELLED("cancelled");

    private final String wireName;

    LangChainStopReason(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static LangChainStopReason fromWireName(String value) {
        Objects.requireNonNull(value, "value must not be null");
        for (LangChainStopReason reason : values()) {
            if (reason.wireName.equals(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown LangChain stop reason: " + value);
    }
}
