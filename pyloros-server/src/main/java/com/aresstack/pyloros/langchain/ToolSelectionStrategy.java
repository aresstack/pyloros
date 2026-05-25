package com.aresstack.pyloros.langchain;

import java.util.List;
import java.util.Map;

/**
 * Selects a subset of tools to expose to the LLM for a given user prompt.
 *
 * <p>Implementations operate on the tool descriptors produced by
 * {@code ToolCatalog.listTools(ToolView)}. The caller is responsible for
 * passing the already filtered list belonging to the {@code llm-agent}
 * view, so that the strategy never sees recursive or restricted tools.
 *
 * <p>Strategies must be deterministic for identical inputs so that the
 * selection can be reproduced and tested.
 */
public interface ToolSelectionStrategy {

    /**
     * Selects the tools that are relevant for {@code question}.
     *
     * @param question        the natural language question / prompt from the user.
     *                        May be {@code null} or blank, in which case the
     *                        strategy must return a fallback result.
     * @param availableTools  the candidate tools (descriptors as returned by the
     *                        tool catalog). Must not be {@code null}; may be empty.
     * @return a {@link ToolSelectionResult} describing the chosen tools and the
     *         reason for the decision. Never {@code null}.
     */
    ToolSelectionResult selectTools(String question, List<Map<String, Object>> availableTools);
}
