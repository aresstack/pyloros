package com.aresstack.pyloros.langchain;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LangChainProviderConfigurationTest {

    @Test
    void validConfiguration() {
        var config = new LangChainProviderConfiguration(
                "pyloros-ai",
                "pyloros-ai/",
                "llm-agent",
                List.of("public"),
                new OllamaConfiguration(URI.create("http://localhost:11434"), "qwen2.5-coder:7b"),
                new LangChainExecutionConfiguration(8, 120, 12000, 1)
        );

        assertEquals("pyloros-ai", config.id());
        assertEquals("pyloros-ai/", config.prefix());
        assertEquals("llm-agent", config.llmAgentToolView());
        assertEquals(List.of("public"), config.exposeInViews());
        assertEquals("qwen2.5-coder:7b", config.ollama().model());
        assertEquals(URI.create("http://localhost:11434"), config.ollama().baseUrl());
        assertEquals(8, config.execution().maxToolCalls());
        assertEquals(120, config.execution().maxRuntimeSeconds());
        assertEquals(12000, config.execution().maxToolResultChars());
        assertEquals(1, config.execution().maxModelRetries());
    }

    @Test
    void missingLlmAgentToolViewThrows() {
        assertThrows(NullPointerException.class, () ->
                new LangChainProviderConfiguration("pyloros-ai", "pyloros-ai/", null, List.of("public"), null, null));
    }

    @Test
    void blankLlmAgentToolViewThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new LangChainProviderConfiguration("pyloros-ai", "pyloros-ai/", "  ", List.of("public"), null, null));
    }

    @Test
    void missingOllamaUsesDefaults() {
        var config = new LangChainProviderConfiguration(
                "pyloros-ai", "pyloros-ai/", "llm-agent", List.of("public"), null, null);

        assertEquals(OllamaConfiguration.DEFAULT_BASE_URL, config.ollama().baseUrl());
        assertEquals(OllamaConfiguration.DEFAULT_MODEL, config.ollama().model());
    }

    @Test
    void invalidOllamaUrlThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaConfiguration(URI.create("ftp://localhost:11434"), "model"));
    }

    @Test
    void defaultModelIsQwen() {
        var ollama = new OllamaConfiguration();
        assertEquals("qwen2.5-coder:7b", ollama.model());
    }
}
