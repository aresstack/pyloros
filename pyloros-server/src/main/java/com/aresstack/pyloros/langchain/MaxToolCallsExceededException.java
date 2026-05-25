package com.aresstack.pyloros.langchain;

/** Thrown when the configured {@code maxToolCalls} limit was exceeded. */
public final class MaxToolCallsExceededException extends LangChainException {

    private final int limit;

    public MaxToolCallsExceededException(int limit, String message) {
        super(LangChainStopReason.MAX_TOOL_CALLS, message);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
