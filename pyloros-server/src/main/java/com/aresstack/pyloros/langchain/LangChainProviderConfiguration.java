package com.aresstack.pyloros.langchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record LangChainProviderConfiguration(
        String id,
        String prefix,
        String llmAgentToolView,
        List<String> exposeInViews,
        OllamaConfiguration ollama,
        LangChainExecutionConfiguration execution
) {

    public LangChainProviderConfiguration {
        id = requireText(id, "id");
        prefix = requireText(prefix, "prefix");
        llmAgentToolView = requireText(llmAgentToolView, "llmAgentToolView");
        exposeInViews = immutableViews(exposeInViews);
        ollama = ollama == null ? new OllamaConfiguration() : ollama;
        execution = execution == null ? new LangChainExecutionConfiguration() : execution;
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
