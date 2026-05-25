package com.aresstack.pyloros.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LangChainProviderJsonConfig(
        String id,
        String type,
        String prefix,
        List<String> exposeInViews,
        String llmAgentToolView,
        OllamaJsonConfig ollama,
        LangChainExecutionJsonConfig execution
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaJsonConfig(
            String baseUrl,
            String model
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LangChainExecutionJsonConfig(
            Integer maxToolCalls,
            Integer maxRuntimeSeconds,
            Integer maxToolResultChars,
            Integer maxModelRetries
    ) {
    }
}
