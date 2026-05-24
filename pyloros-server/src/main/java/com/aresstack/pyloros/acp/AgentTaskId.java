package com.aresstack.pyloros.acp;

import java.util.Objects;
import java.util.UUID;

public record AgentTaskId(String value) {

    public AgentTaskId {
        Objects.requireNonNull(value, "value must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        UUID.fromString(normalizedValue);
        value = normalizedValue;
    }

    public static AgentTaskId generate() {
        return new AgentTaskId(UUID.randomUUID().toString());
    }

    public static AgentTaskId of(String value) {
        return new AgentTaskId(value);
    }
}
