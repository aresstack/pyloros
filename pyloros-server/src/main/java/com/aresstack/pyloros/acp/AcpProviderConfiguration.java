package com.aresstack.pyloros.acp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AcpProviderConfiguration(
        String id,
        String prefix,
        String agentToolView,
        List<String> exposeInViews,
        AcpProcessConfiguration process,
        AcpExecutionConfiguration execution
) {

    public AcpProviderConfiguration {
        id = requireText(id, "id");
        prefix = requireText(prefix, "prefix");
        agentToolView = requireText(agentToolView, "agentToolView");
        exposeInViews = immutableViews(exposeInViews);
        process = Objects.requireNonNull(process, "process must not be null");
        execution = execution == null ? new AcpExecutionConfiguration() : execution;
    }

    private static List<String> immutableViews(List<String> exposeInViews) {
        if (exposeInViews == null || exposeInViews.isEmpty()) {
            return List.of();
        }

        List<String> normalizedViews = new ArrayList<>();
        for (String exposeInView : exposeInViews) {
            normalizedViews.add(requireText(exposeInView, "exposeInViews entry"));
        }
        return List.copyOf(normalizedViews);
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
