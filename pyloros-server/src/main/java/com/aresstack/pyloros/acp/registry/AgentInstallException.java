package com.aresstack.pyloros.acp.registry;

/**
 * Thrown when an agent installation materialization or removal fails.
 */
public class AgentInstallException extends RuntimeException {

    public AgentInstallException(String message) {
        super(message);
    }

    public AgentInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
