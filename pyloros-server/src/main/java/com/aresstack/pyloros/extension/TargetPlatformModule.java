package com.aresstack.pyloros.extension;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.List;

public interface TargetPlatformModule {

    String moduleId();

    String displayName();

    default List<String> targetPlatforms() {
        return List.of(moduleId());
    }

    default List<String> futureAdapterNames() {
        return List.of(
                "copilot-cli",
                "claude-cli",
                "continue-cli",
                "google-cli",
                "vscode"
        );
    }

    default List<ToolProvider> toolProviders() {
        return List.of();
    }

    default List<TargetPlatformSkill> skills() {
        return List.of();
    }
}
