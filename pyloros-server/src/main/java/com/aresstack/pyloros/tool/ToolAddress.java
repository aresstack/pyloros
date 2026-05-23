package com.aresstack.pyloros.tool;

import java.util.Objects;

public record ToolAddress(
        String providerId,
        String upstreamToolName
) {

    public ToolAddress {
        providerId = require(providerId, "providerId");
        upstreamToolName = require(upstreamToolName, "upstreamToolName");
    }

    private static String require(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
