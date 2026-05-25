package com.aresstack.pyloros.langchain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a {@link ToolSelectionStrategy} invocation.
 *
 * <p>The selection is either a non-empty list of chosen tools (descriptors as
 * returned by the tool catalog) together with a short human readable
 * {@link #reason()}, or an empty list with {@link #fallback()} set to
 * {@code true}. The latter signals to the caller that the strategy did not
 * find any matching tool and the LLM should either answer without tools or
 * surface a clear hint to the user.
 */
public record ToolSelectionResult(
        List<Map<String, Object>> selectedTools,
        String reason,
        boolean fallback
) {

    public ToolSelectionResult {
        Objects.requireNonNull(selectedTools, "selectedTools must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        selectedTools = List.copyOf(selectedTools);
    }

    /**
     * Convenience factory for a successful selection.
     */
    public static ToolSelectionResult selected(List<Map<String, Object>> tools, String reason) {
        return new ToolSelectionResult(tools, reason, false);
    }

    /**
     * Convenience factory for an empty selection. Use this when the strategy
     * could not find a matching tool. The reason is propagated to the caller
     * so it can be logged or surfaced to the user.
     */
    public static ToolSelectionResult fallback(String reason) {
        return new ToolSelectionResult(List.of(), reason, true);
    }

    public boolean isEmpty() {
        return selectedTools.isEmpty();
    }

    public int size() {
        return selectedTools.size();
    }
}
