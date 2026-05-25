package com.aresstack.pyloros.langchain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MVP {@link ToolSelectionStrategy} that scores tools by keyword overlap
 * between the question and the tool's external name plus description.
 *
 * <p>The strategy is intentionally simple so that the selection is
 * deterministic and easy to debug:
 * <ol>
 *   <li>The question and each tool descriptor are tokenized into lowercase
 *       alphanumeric tokens (length &ge; 3, stopwords removed).</li>
 *   <li>Each tool's score equals the number of distinct question tokens that
 *       also appear in the tool's tokenized name and description.</li>
 *   <li>Tools with score 0 are discarded.</li>
 *   <li>Tools whose external name appears in {@link #excludedTools()} are
 *       discarded.</li>
 *   <li>Remaining tools are sorted by score descending, then by name
 *       ascending, and the top {@link #maxTools()} are returned.</li>
 * </ol>
 *
 * <p>If the question is blank, the available list is empty, or no tool
 * matches, the strategy returns a {@link ToolSelectionResult#fallback(String)
 * fallback} result.
 */
public final class KeywordToolSelectionStrategy implements ToolSelectionStrategy {

    /** Default upper bound on the number of selected tools. */
    public static final int DEFAULT_MAX_TOOLS = 5;

    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private static final Set<String> STOPWORDS = Set.of(
            // English
            "the", "and", "for", "with", "from", "into", "out", "any", "all", "you", "your",
            "are", "was", "were", "but", "not", "can", "how", "what", "when", "where", "why",
            "who", "which", "that", "this", "these", "those", "have", "has", "had", "will",
            "would", "should", "could", "about", "there", "their", "them", "they",
            // German
            "und", "oder", "aber", "der", "die", "das", "den", "dem", "des", "ein", "eine",
            "einen", "einem", "einer", "eines", "ist", "sind", "war", "wie", "wer",
            "wann", "wenn", "weil", "fuer", "fur", "mit", "von", "zur", "zum", "auf", "aus",
            "bei", "ueber", "uber", "nach", "vor", "ich", "wir", "ihr", "sie", "kann"
    );

    private final int maxTools;
    private final Set<String> excludedTools;

    public KeywordToolSelectionStrategy() {
        this(DEFAULT_MAX_TOOLS, Set.of());
    }

    public KeywordToolSelectionStrategy(int maxTools, Set<String> excludedTools) {
        if (maxTools <= 0) {
            throw new IllegalArgumentException("maxTools must be > 0, was " + maxTools);
        }
        this.maxTools = maxTools;
        this.excludedTools = Set.copyOf(Objects.requireNonNull(excludedTools, "excludedTools must not be null"));
    }

    public int maxTools() {
        return maxTools;
    }

    public Set<String> excludedTools() {
        return excludedTools;
    }

    @Override
    public ToolSelectionResult selectTools(String question, List<Map<String, Object>> availableTools) {
        Objects.requireNonNull(availableTools, "availableTools must not be null");

        if (availableTools.isEmpty()) {
            return ToolSelectionResult.fallback("no tools available in view");
        }

        Set<String> queryTokens = tokenize(question);
        if (queryTokens.isEmpty()) {
            return ToolSelectionResult.fallback("question contained no meaningful keywords");
        }

        List<ScoredTool> scored = new ArrayList<>();
        for (Map<String, Object> tool : availableTools) {
            if (tool == null) {
                continue;
            }
            String name = stringValue(tool.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            if (excludedTools.contains(name)) {
                continue;
            }
            String description = stringValue(tool.get("description"));
            Set<String> toolTokens = tokenize(name + " " + (description == null ? "" : description));
            int score = 0;
            for (String token : queryTokens) {
                if (toolTokens.contains(token)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new ScoredTool(tool, name, score));
            }
        }

        if (scored.isEmpty()) {
            return ToolSelectionResult.fallback("no tool matched the question keywords");
        }

        scored.sort(Comparator
                .comparingInt(ScoredTool::score).reversed()
                .thenComparing(ScoredTool::name));

        List<Map<String, Object>> selected = new ArrayList<>(Math.min(maxTools, scored.size()));
        for (ScoredTool entry : scored) {
            if (selected.size() >= maxTools) {
                break;
            }
            selected.add(entry.tool());
        }
        return ToolSelectionResult.selected(selected, "keyword match");
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : TOKEN_SEPARATOR.split(text.toLowerCase(Locale.ROOT))) {
            if (raw.length() < 3) {
                continue;
            }
            if (STOPWORDS.contains(raw)) {
                continue;
            }
            tokens.add(raw);
        }
        return tokens;
    }

    private static String stringValue(Object value) {
        return value instanceof String s ? s : null;
    }

    private record ScoredTool(Map<String, Object> tool, String name, int score) {
    }
}
