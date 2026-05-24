package com.aresstack.pyloros.acp;

public record AcpExecutionConfiguration(
        int defaultTaskTimeoutSeconds,
        int maxEventsPerTask,
        int maxEventTextChars,
        int maxResultTextChars
) {

    public AcpExecutionConfiguration() {
        this(900, 1000, 12000, 24000);
    }

    public AcpExecutionConfiguration {
        if (defaultTaskTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("defaultTaskTimeoutSeconds must be greater than 0");
        }
        if (maxEventsPerTask <= 0) {
            throw new IllegalArgumentException("maxEventsPerTask must be greater than 0");
        }
        if (maxEventTextChars <= 0) {
            throw new IllegalArgumentException("maxEventTextChars must be greater than 0");
        }
        if (maxResultTextChars <= 0) {
            throw new IllegalArgumentException("maxResultTextChars must be greater than 0");
        }
    }
}
