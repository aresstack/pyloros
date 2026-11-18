package com.aresstack.pyloros.acp;

import java.util.HashSet;
import java.util.Set;

/**
 * Keep ACP tool declarations small enough for Antigravity's eager-tool context.
 */
public final class AcpDirectToolPolicy {

    private static final Set<String> READ_ONLY = Set.of(
            "intellij__get_repositories",
            "intellij__get_file_text_by_path",
            "intellij__list_directory_tree",
            "intellij__find_files_by_name_keyword",
            "intellij_index__ide_project_status",
            "intellij_index__ide_diagnostics",
            "github__search_code",
            "github__get_file_contents"
    );

    private static final Set<String> MUTATING = Set.of(
            "intellij__execute_terminal_command",
            "intellij__replace_text_in_file",
            "intellij__create_new_file",
            "intellij__run_gradle_task"
    );

    private AcpDirectToolPolicy() {
    }

    public static Set<String> directToolNames() {
        Set<String> tools = new HashSet<>(READ_ONLY);
        tools.addAll(MUTATING);
        return Set.copyOf(tools);
    }

    public static Set<String> readOnlyToolNames() {
        return READ_ONLY;
    }
}
