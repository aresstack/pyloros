package com.aresstack.pyloros.langchain;

import java.util.Objects;
import java.util.UUID;

public record LangChainTaskId(String value) {

    public LangChainTaskId {
        Objects.requireNonNull(value, "value must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        UUID.fromString(normalizedValue);
        value = normalizedValue;
    }

    public static LangChainTaskId generate() {
        return new LangChainTaskId(UUID.randomUUID().toString());
    }

    public static LangChainTaskId of(String value) {
        return new LangChainTaskId(value);
    }
}
