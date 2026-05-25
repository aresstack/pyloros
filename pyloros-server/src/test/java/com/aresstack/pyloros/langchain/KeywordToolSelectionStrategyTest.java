package com.aresstack.pyloros.langchain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordToolSelectionStrategyTest {

    private static Map<String, Object> tool(String name, String description) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        return tool;
    }

    private static final List<Map<String, Object>> SAMPLE_TOOLS = List.of(
            tool("github/create_issue", "Create a new GitHub issue in a repository"),
            tool("github/list_pull_requests", "List GitHub pull requests on a branch"),
            tool("intellij/get_problems", "Return the current IntelliJ inspection problems"),
            tool("intellij/run_tests", "Run unit tests inside IntelliJ"),
            tool("pyloros/ping", "Ping the Pyloros server")
    );

    @Test
    void selectsGithubToolsForGithubQuestion() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy();

        ToolSelectionResult result = strategy.selectTools(
                "How can I create a GitHub issue?", SAMPLE_TOOLS);

        assertFalse(result.isEmpty());
        assertFalse(result.fallback());
        List<String> names = result.selectedTools().stream()
                .map(t -> (String) t.get("name"))
                .toList();
        assertTrue(names.contains("github/create_issue"),
                "expected github/create_issue, got " + names);
        // top-ranked tool should match the most question tokens
        assertEquals("github/create_issue", names.get(0));
        // pyloros/ping shares no tokens with the question, must not be selected
        assertFalse(names.contains("pyloros/ping"));
    }

    @Test
    void selectsIntellijToolsForIntellijQuestion() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy();

        ToolSelectionResult result = strategy.selectTools(
                "Welche Probleme zeigt IntelliJ aktuell an?", SAMPLE_TOOLS);

        assertFalse(result.isEmpty());
        List<String> names = result.selectedTools().stream()
                .map(t -> (String) t.get("name"))
                .toList();
        assertTrue(names.contains("intellij/get_problems"),
                "expected intellij/get_problems, got " + names);
        assertFalse(names.contains("github/create_issue"));
    }

    @Test
    void returnsFallbackForUnknownQuestion() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy();

        ToolSelectionResult result = strategy.selectTools(
                "Bitte erzaehl mir einen Witz.", SAMPLE_TOOLS);

        assertTrue(result.isEmpty());
        assertTrue(result.fallback());
        assertNotNull(result.reason());
        assertFalse(result.reason().isBlank());
    }

    @Test
    void returnsFallbackForBlankQuestion() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy();

        ToolSelectionResult result = strategy.selectTools("   ", SAMPLE_TOOLS);

        assertTrue(result.isEmpty());
        assertTrue(result.fallback());
    }

    @Test
    void returnsFallbackForEmptyToolList() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy();

        ToolSelectionResult result = strategy.selectTools("Open a GitHub issue", List.of());

        assertTrue(result.isEmpty());
        assertTrue(result.fallback());
    }

    @Test
    void enforcesMaxToolLimit() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy(2, Set.of());

        // every tool matches at least one of these tokens
        ToolSelectionResult result = strategy.selectTools(
                "github intellij pyloros issue tests pull problems", SAMPLE_TOOLS);

        assertFalse(result.fallback());
        assertEquals(2, result.selectedTools().size());
    }

    @Test
    void doesNotSelectExcludedTools() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy(
                KeywordToolSelectionStrategy.DEFAULT_MAX_TOOLS,
                Set.of("github/create_issue"));

        ToolSelectionResult result = strategy.selectTools(
                "How do I create a github issue?", SAMPLE_TOOLS);

        List<String> names = result.selectedTools().stream()
                .map(t -> (String) t.get("name"))
                .toList();
        assertFalse(names.contains("github/create_issue"),
                "excluded tool must not appear, got " + names);
    }

    @Test
    void selectionIsDeterministic() {
        ToolSelectionStrategy strategy = new KeywordToolSelectionStrategy();
        String question = "List github pull requests and create a github issue";

        ToolSelectionResult first = strategy.selectTools(question, SAMPLE_TOOLS);
        ToolSelectionResult second = strategy.selectTools(question, SAMPLE_TOOLS);

        List<String> firstNames = first.selectedTools().stream()
                .map(t -> (String) t.get("name")).toList();
        List<String> secondNames = second.selectedTools().stream()
                .map(t -> (String) t.get("name")).toList();
        assertEquals(firstNames, secondNames);
    }

    @Test
    void rejectsNonPositiveMaxTools() {
        assertThrows(IllegalArgumentException.class,
                () -> new KeywordToolSelectionStrategy(0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new KeywordToolSelectionStrategy(-1, Set.of()));
    }

    @Test
    void strategyIsInterchangeable() {
        // Verifies the interface contract: a custom strategy can be plugged in.
        ToolSelectionStrategy alwaysEmpty =
                (q, tools) -> ToolSelectionResult.fallback("custom");
        ToolSelectionResult result = alwaysEmpty.selectTools("anything", SAMPLE_TOOLS);

        assertTrue(result.fallback());
        assertEquals("custom", result.reason());
    }

    @Test
    void resultIsImmutable() {
        ToolSelectionResult result = ToolSelectionResult.selected(
                List.of(tool("a/b", "x")), "ok");
        assertThrows(UnsupportedOperationException.class,
                () -> result.selectedTools().add(tool("c/d", "y")));
    }
}
