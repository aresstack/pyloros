package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record InstallPlan(
        String command,
        List<String> args,
        String installPath,
        Map<String, String> sourceMetadata
) {

    public InstallPlan {
        command = requireText(command, "command");
        args = args == null ? List.of() : List.copyOf(args);
        installPath = requireText(installPath, "installPath");
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
