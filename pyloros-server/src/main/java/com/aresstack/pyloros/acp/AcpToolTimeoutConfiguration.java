package com.aresstack.pyloros.acp;

public record AcpToolTimeoutConfiguration(int timeoutSeconds) {

    public AcpToolTimeoutConfiguration() {
        this(900);
    }

    public AcpToolTimeoutConfiguration {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
        }
    }
}
