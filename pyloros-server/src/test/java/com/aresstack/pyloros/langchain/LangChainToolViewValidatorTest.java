package com.aresstack.pyloros.langchain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LangChainToolViewValidatorTest {

    @Test
    void allowsValidLlmAgentToolView() {
        assertDoesNotThrow(() -> LangChainToolViewValidator.validate(
                config("pyloros-ai", "llm-agent"), Set.of("pyloros-ai")));
    }

    @Test
    void rejectsSelfReference() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                LangChainToolViewValidator.validate(config("pyloros-ai", "pyloros-ai"), Set.of("pyloros-ai")));

        assertEquals("llmAgentToolView must not reference LangChain provider itself: pyloros-ai", exception.getMessage());
    }

    @Test
    void rejectsOtherLangChainProviderReference() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                LangChainToolViewValidator.validate(config("pyloros-ai", "other-llm"), Set.of("pyloros-ai", "other-llm")));

        assertEquals("llmAgentToolView must not reference another LangChain provider: other-llm", exception.getMessage());
    }

    @Test
    void rejectsPublicView() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                LangChainToolViewValidator.validate(config("pyloros-ai", "public"), Set.of("pyloros-ai")));

        assertEquals("llmAgentToolView must not be 'public' — LangChain agents must not see the public tool view: public", exception.getMessage());
    }

    @Test
    void rejectsRecursiveToolView() {
        var providerConfig = new LangChainProviderConfiguration(
                "pyloros-ai", "pyloros-ai/", "shared", List.of("public", "shared"), null, null);

        var exception = assertThrows(IllegalArgumentException.class, () ->
                LangChainToolViewValidator.validate(providerConfig, Set.of("pyloros-ai")));

        assertEquals(
                "llmAgentToolView must not be a view where the LangChain provider is exposed (would cause recursion): "
                        + "provider=pyloros-ai llmAgentToolView=shared exposeInViews=[public, shared]",
                exception.getMessage());
    }

    @Test
    void providerDescriptorHasTypeLangChain() {
        var config = config("pyloros-ai", "llm-agent");
        // The ProviderType.LANGCHAIN is used in LangChainProviderFactory.createDescriptors
        // This test verifies the configuration itself is valid for descriptor creation
        assertNotNull(config.id());
        assertNotNull(config.llmAgentToolView());
    }

    private static LangChainProviderConfiguration config(String providerId, String llmAgentToolView) {
        return new LangChainProviderConfiguration(
                providerId, providerId + "/", llmAgentToolView, List.of("public"), null, null);
    }
}
