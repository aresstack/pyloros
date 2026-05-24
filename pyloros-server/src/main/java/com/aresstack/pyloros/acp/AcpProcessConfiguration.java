package com.aresstack.pyloros.acp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AcpProcessConfiguration(
        String command,
        List<String> args,
        String workingDirectory,
        Map<String, String> environment
) {

    public AcpProcessConfiguration {
        command = requireText(command, "command");
        args = immutableArgs(args);
        environment = immutableEnvironment(environment);
    }

    private static List<String> immutableArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }

        List<String> normalizedArgs = new ArrayList<>();
        for (String arg : args) {
            normalizedArgs.add(Objects.requireNonNull(arg, "args must not contain null values"));
        }
        return List.copyOf(normalizedArgs);
    }

    private static Map<String, String> immutableEnvironment(Map<String, String> environment) {
        if (environment == null || environment.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalizedEnvironment = new LinkedHashMap<>();
        environment.forEach((key, value) -> normalizedEnvironment.put(
                Objects.requireNonNull(key, "environment key must not be null"),
                Objects.requireNonNull(value, "environment value must not be null")));
        return Map.copyOf(normalizedEnvironment);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }
}
