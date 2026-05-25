package com.aresstack.pyloros.langchain;

/** Thrown when the run was cancelled (e.g. by an explicit client request). */
public final class CancelledException extends LangChainException {

    public CancelledException(String message) {
        super(LangChainStopReason.CANCELLED, message);
    }
}
