# Pyloros IntelliJ MCP Enablement

This module documents the recommended IntelliJ MCP setup for working on Pyloros with AI agents.

It is intentionally documentation-only. It exists so IntelliJ imports it as a visible project module, while the actual project runtime remains unchanged.

## Recommended IntelliJ Plugins

| Plugin | Purpose |
| --- | --- |
| MCP Steroid | Executes Kotlin inside the IntelliJ runtime so agents can use PSI, VFS, inspections, refactorings, build, tests, and debugger APIs. |
| IDE Index MCP Server | Exposes fast semantic index operations such as find references, move file, safe delete, hierarchy, diagnostics, and build data. |
| MCP Server AI Companion | Companion tooling for IntelliJ MCP workflows and richer IDE interaction. |
| Built-in IntelliJ MCP Server | JetBrains' integrated MCP server. Enable it in IntelliJ Settings before use. |
| GitHub MCP Server | Gives agents repository, issue, pull request, code search, and review capabilities. |

Plugin pages:

- https://plugins.jetbrains.com/plugin/30019-mcp-steroid
- https://plugins.jetbrains.com/plugin/29174-ide-index-mcp-server
- https://plugins.jetbrains.com/plugin/31060-mcp-server-ai-companion

## Required Local Setup

1. Install the recommended plugins.
2. Enable the built-in IntelliJ MCP Server in IntelliJ Settings.
3. Start or verify each local MCP endpoint.
4. Copy `config/mcp.template.json` to your user-level MCP config location or to an ignored local file.
5. Replace placeholders locally only. Do not commit real tokens.
6. Add project-specific skills from `skills/` to the agent's skill/context mechanism.

## Security Notes

Never commit access tokens, GitHub PATs, Copilot tokens, or local machine-specific secrets.

Use a local ignored file such as:

```text
pyloros-intellij-mcp/config/mcp.local.json
```

or the user-level Copilot/agent config directory.

If a token was pasted into chat, logs, or a committed file, rotate it.

## Included Skills

- `skills/intellij-mcp-steroids-clean-code-refactoring.md` — Clean Code refactoring workflow using IntelliJ PSI and MCP Steroids.

## Suggested Agent Workflow

1. Inspect target code through IntelliJ PSI/indexes.
2. Identify one small Clean Code refactoring.
3. Dry-run the refactoring processor.
4. Apply only if preparation succeeds.
5. Save documents.
6. Run inspections.
7. Build.
8. Report what changed, why, and validation results.
