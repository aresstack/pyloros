package com.aresstack.pyloros.acp;

import java.util.Objects;

public final class AcpProcessFailure extends RuntimeException {

    private final String reason;
    private final String command;
    private final String detail;

    public AcpProcessFailure(String reason, String command, String detail) {
        super(message(reason, command, detail));
        this.reason = requireText(reason, "reason");
        this.command = requireText(command, "command");
        this.detail = requireDetail(detail);
    }

    public AcpProcessFailure(String reason, String command, String detail, Throwable cause) {
        super(message(reason, command, detail), cause);
        this.reason = requireText(reason, "reason");
        this.command = requireText(command, "command");
        this.detail = requireDetail(detail);
    }

    public String reason() {
        return reason;
    }

    public String command() {
        return command;
    }

    public String detail() {
        return detail;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static String requireDetail(String detail) {
        Objects.requireNonNull(detail, "detail must not be null");
        return detail;
    }

    private static String message(String reason, String command, String detail) {
        return requireText(reason, "reason") + ": command=" + requireText(command, "command") + ", detail=" + requireDetail(detail);
    }
}
