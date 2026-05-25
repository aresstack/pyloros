package com.aresstack.pyloros.langchain;

public record LangChainExecutionConfiguration(
        int maxToolCalls,
        int maxRuntimeSeconds,
        int maxToolResultChars,
        int maxModelRetries
) {

    public LangChainExecutionConfiguration() {
        this(8, 120, 12000, 1);
    }

    public LangChainExecutionConfiguration {
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("maxToolCalls must be greater than 0");
        }
        if (maxRuntimeSeconds <= 0) {
            throw new IllegalArgumentException("maxRuntimeSeconds must be greater than 0");
        }
        if (maxToolResultChars <= 0) {
            throw new IllegalArgumentException("maxToolResultChars must be greater than 0");
        }
        if (maxModelRetries < 0) {
            throw new IllegalArgumentException("maxModelRetries must not be negative");
        }
    }
}
