package com.aresstack.pyloros.langchain;

import java.util.Objects;

/** Thrown when a tool selected by the LLM failed during execution. */
public final class ToolExecutionException extends LangChainException {

    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super(LangChainStopReason.TOOL_ERROR, message);
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(LangChainStopReason.TOOL_ERROR, message, cause);
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
    }

    public String toolName() {
        return toolName;
    }
}
