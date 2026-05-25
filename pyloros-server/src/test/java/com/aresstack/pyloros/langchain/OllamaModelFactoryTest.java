package com.aresstack.pyloros.langchain;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class OllamaModelFactoryTest {

    @Test
    void createsModelWithDefaults() {
        ChatModel model = OllamaModelFactory.createDefault();

        assertNotNull(model);
    }

    @Test
    void createsModelWithCustomConfig() {
        var config = new OllamaModelConfiguration(
                "http://my-ollama:11434",
                "llama3:8b",
                0.5,
                Duration.ofSeconds(60)
        );

        ChatModel model = OllamaModelFactory.create(config);

        assertNotNull(model);
    }

    @Test
    void rejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> OllamaModelFactory.create(null));
    }

    @Test
    void rejectsInvalidBaseUrl() {
        var config = new OllamaModelConfiguration(
                "not-a-url",
                "qwen2.5-coder:7b",
                0.1,
                Duration.ofSeconds(120)
        );

        var ex = assertThrows(OllamaModelException.class, () -> OllamaModelFactory.create(config));
        assertTrue(ex.getMessage().contains("Invalid Ollama base URL"));
    }

    @Test
    void rejectsFtpScheme() {
        var config = new OllamaModelConfiguration(
                "ftp://localhost:11434",
                "qwen2.5-coder:7b",
                0.1,
                Duration.ofSeconds(120)
        );

        var ex = assertThrows(OllamaModelException.class, () -> OllamaModelFactory.create(config));
        assertTrue(ex.getMessage().contains("scheme must be http or https"));
    }

    @Test
    void defaultConfigHasExpectedValues() {
        var config = OllamaModelConfiguration.defaults();

        assertEquals("http://localhost:11434", config.baseUrl());
        assertEquals("qwen2.5-coder:7b", config.modelName());
        assertEquals(0.1, config.temperature());
        assertEquals(Duration.ofSeconds(120), config.timeout());
    }

    @Test
    void configRejectsBlankBaseUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaModelConfiguration("  ", "model", 0.1, Duration.ofSeconds(30)));
    }

    @Test
    void configRejectsBlankModelName() {
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaModelConfiguration("http://localhost:11434", "  ", 0.1, Duration.ofSeconds(30)));
    }

    @Test
    void configRejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaModelConfiguration("http://localhost:11434", "model", 0.1, Duration.ofSeconds(-1)));
    }

    @Test
    void configRejectsZeroTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaModelConfiguration("http://localhost:11434", "model", 0.1, Duration.ZERO));
    }
}
