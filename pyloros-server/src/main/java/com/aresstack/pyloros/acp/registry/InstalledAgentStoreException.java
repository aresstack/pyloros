package com.aresstack.pyloros.acp.registry;

/**
 * Structured exception for installed agent store errors.
 * Prevents silent misconfiguration by surfacing corruption and I/O failures.
 */
public final class InstalledAgentStoreException extends RuntimeException {

    public enum Kind {
        IO_ERROR,
        CORRUPTED,
        VALIDATION_ERROR
    }

    private final Kind kind;

    public InstalledAgentStoreException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public InstalledAgentStoreException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
