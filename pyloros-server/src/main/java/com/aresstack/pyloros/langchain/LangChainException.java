package com.aresstack.pyloros.langchain;

import java.util.Objects;

/**
 * Base class for failures inside the LangChain provider that can be mapped
 * directly to a {@link LangChainStopReason} when producing an MCP tool result.
 *
 * <p>The {@link #getMessage() message} is intended to be a short, user-facing
 * description. Stack traces, secrets and full technical dumps must <strong>not</strong>
 * be included here because the message will be surfaced to clients via the
 * MCP tool result.
 */
public abstract class LangChainException extends RuntimeException {

    private final LangChainStopReason stopReason;

    protected LangChainException(LangChainStopReason stopReason, String message) {
        super(message);
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
    }

    protected LangChainException(LangChainStopReason stopReason, String message, Throwable cause) {
        super(message, cause);
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
    }

    public LangChainStopReason stopReason() {
        return stopReason;
    }
}
