package com.aresstack.pyloros.tool;

import java.util.Objects;

public final class ToolNameFormatter {

    public static final String DEFAULT_SEPARATOR = "__";

    private final String separator;

    public ToolNameFormatter(String separator) {
        this.separator = require(separator, "separator");
    }

    public static ToolNameFormatter defaultFormatter() {
        return new ToolNameFormatter(DEFAULT_SEPARATOR);
    }

    public String separator() {
        return separator;
    }

    public String externalName(String providerId, String upstreamToolName) {
        return require(providerId, "providerId") + separator + require(upstreamToolName, "upstreamToolName");
    }

    private static String require(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

