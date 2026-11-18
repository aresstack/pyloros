package com.aresstack.pyloros.acp;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AcpDirectToolPolicyTest {

    @Test
    void exposesSupportedGithubToolNames() {
        Set<String> tools = AcpDirectToolPolicy.directToolNames();

        assertTrue(tools.contains("github__search_code"));
        assertTrue(tools.contains("github__get_file_contents"));
        assertFalse(tools.contains("github__search"));
        assertFalse(tools.contains("github__fetch_file"));
        assertEquals(12, tools.size());
    }

    @Test
    void requiresApprovalForToolsThatCanModifyTheProject() {
        Set<String> readOnly = AcpDirectToolPolicy.readOnlyToolNames();

        assertTrue(AcpDirectToolPolicy.directToolNames().containsAll(readOnly));
        assertTrue(Collections.disjoint(readOnly, Set.of(
                "intellij__execute_terminal_command",
                "intellij__replace_text_in_file",
                "intellij__create_new_file",
                "intellij__run_gradle_task")));
    }
}
