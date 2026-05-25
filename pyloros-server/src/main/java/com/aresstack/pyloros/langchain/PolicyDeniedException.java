package com.aresstack.pyloros.langchain;

import java.util.Objects;

/** Thrown when a Pyloros policy denied a tool invocation requested by the LLM. */
public final class PolicyDeniedException extends LangChainException {

    private final String toolName;

    public PolicyDeniedException(String toolName, String message) {
        super(LangChainStopReason.POLICY_DENIED, message);
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
    }

    public String toolName() {
        return toolName;
    }
}
