package com.aresstack.pyloros.langchain;

/** Thrown when the configured runtime limit elapsed before the run could complete. */
public final class LangChainTimeoutException extends LangChainException {

    public LangChainTimeoutException(String message) {
        super(LangChainStopReason.TIMEOUT, message);
    }

    public LangChainTimeoutException(String message, Throwable cause) {
        super(LangChainStopReason.TIMEOUT, message, cause);
    }
}
