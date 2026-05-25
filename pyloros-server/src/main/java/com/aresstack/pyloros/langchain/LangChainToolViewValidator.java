package com.aresstack.pyloros.langchain;

import java.util.Objects;
import java.util.Set;

/**
 * Validates that a LangChain provider's llmAgentToolView does not create recursion:
 * <ul>
 *   <li>Must not be 'public' — LLM agents must never see the public view</li>
 *   <li>Must not reference the provider's own ID</li>
 *   <li>Must not reference another LangChain provider ID</li>
 *   <li>Must not be a view where the LangChain provider's own tools are exposed (prevents the agent from seeing itself)</li>
 * </ul>
 */
public final class LangChainToolViewValidator {

    private LangChainToolViewValidator() {
    }

    /**
     * Validates that the given provider configuration does not create a recursive tool view.
     * @param config the LangChain provider configuration
     * @param allLangChainProviderIds set of all LangChain provider IDs in the system
     * @throws IllegalArgumentException if recursion is detected or public view is used
     */
    public static void validate(LangChainProviderConfiguration config, Set<String> allLangChainProviderIds) {
        LangChainProviderConfiguration providerConfig = Objects.requireNonNull(config, "config must not be null");
        Set<String> providerIds = Set.copyOf(Objects.requireNonNull(allLangChainProviderIds, "allLangChainProviderIds must not be null"));
        String llmAgentToolView = providerConfig.llmAgentToolView();

        if ("public".equalsIgnoreCase(llmAgentToolView)) {
            throw new IllegalArgumentException(
                    "llmAgentToolView must not be 'public' — LangChain agents must not see the public tool view: " + llmAgentToolView);
        }
        if (providerConfig.id().equals(llmAgentToolView)) {
            throw new IllegalArgumentException("llmAgentToolView must not reference LangChain provider itself: " + llmAgentToolView);
        }
        if (providerIds.contains(llmAgentToolView)) {
            throw new IllegalArgumentException("llmAgentToolView must not reference another LangChain provider: " + llmAgentToolView);
        }
        if (providerConfig.exposeInViews().contains(llmAgentToolView)) {
            throw new IllegalArgumentException(
                    "llmAgentToolView must not be a view where the LangChain provider is exposed (would cause recursion): "
                            + "provider=" + providerConfig.id() + " llmAgentToolView=" + llmAgentToolView
                            + " exposeInViews=" + providerConfig.exposeInViews());
        }
    }
}
