package com.aresstack.pyloros.langchain;

public enum LangChainTaskState {
    CREATED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}
