package com.aresstack.pyloros.intellij;

import com.aresstack.pyloros.extension.TargetPlatformModule;
import com.aresstack.pyloros.extension.TargetPlatformSkill;
import com.aresstack.pyloros.tool.ToolProvider;

import java.util.List;

public final class IntellijTargetPlatformModule implements TargetPlatformModule {

    @Override
    public String moduleId() {
        return "intellij";
    }

    @Override
    public String displayName() {
        return "IntelliJ IDEA MCP Target Platform";
    }

    @Override
    public List<String> targetPlatforms() {
        return List.of(
                "intellij",
                "copilot-cli",
                "claude-cli",
                "continue-cli",
                "google-cli",
                "vscode"
        );
    }

    @Override
    public List<ToolProvider> toolProviders() {
        return List.of(new IntellijMcpSetupToolProvider());
    }

    @Override
    public List<TargetPlatformSkill> skills() {
        return List.of(new TargetPlatformSkill(
                "intellij-mcp-steroids-clean-code-refactoring",
                "IntelliJ MCP Steroids Clean Code Refactoring",
                "Use IntelliJ PSI and refactoring processors for Java Clean Code refactorings with dry-run, apply, inspections, and build validation.",
                SkillTexts.CLEAN_CODE_REFACTORING,
                moduleId()
        ));
    }
}
