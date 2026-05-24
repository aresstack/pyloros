package com.aresstack.pyloros.acp;

public enum AgentTaskState {
    CREATED,
    RUNNING,
    WAITING_FOR_PERMISSION,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}
