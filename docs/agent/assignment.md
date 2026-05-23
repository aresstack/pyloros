# Current Assignment

Task: 009-B - Prove multi-MCP upstream aggregation with GitHub MCP

Read AGENTS.md first. Use this file as the single source of truth.

## High-Level Architecture Source

Use `docs/requirements/005-acp-extension.md` as the high-level architecture for this phase.

Pyloros is a headless Agent Capability Gateway:

```text
External clients speak MCP to Pyloros.
Internally Pyloros aggregates ToolProviders.
ToolProviders may be real MCP upstreams, virtual ACP providers, native Java providers, or later other adapters.
```

This task proves the first real aggregation milestone:

```text
ChatGPT
  -> Pyloros
    -> Native Pyloros tools
    -> IntelliJ MCP upstream
    -> GitHub MCP upstream
```

Do not implement ACP in this task.

## Context

Task 009-A introduced the ToolCatalog architecture:

```text
ProviderRegistry
ToolCatalog
ToolCatalogEntry
ToolAddress
ToolRouter
```

Now prove that the architecture supports more than one real MCP upstream.

Available GitHub MCP upstream configuration example:

```json
"github": {
  "type": "http",
  "url": "https://api.githubcopilot.com/mcp/",
  "requestInit": {
    "headers": {
      "Authorization": "Bearer XXX"
    }
  }
}
```

The token must never be committed, logged, or written into tracked files.

## Goal

Add GitHub MCP as a second real MCP upstream provider and prove that Pyloros exposes one aggregated MCP tool catalog containing native, IntelliJ, and GitHub tools.

## Product Requirement

The public client must see one MCP server: Pyloros.

`tools/list` should contain tools from multiple providers, for example:

```text
pyloros__ping
intellij/get_project_modules
github/search_repositories
github/list_issues
```

Actual GitHub tool names may differ. Preserve upstream tool names under the `github/` namespace.

## Scope

### 1. Introduce generic MCP upstream abstractions if needed

The existing IntelliJ upstream classes may still be IDEA-specific.

For this task, either:

- minimally generalize the current upstream client/provider so it can support GitHub too, or
- introduce generic classes and keep IntelliJ as a configuration/specialization.

Preferred target names if practical:

```text
McpUpstreamConfig
McpUpstreamClient
McpSseSession / McpHttpTransport as needed
McpJsonRpcClient
McpUpstreamToolProvider
```

Do not over-refactor. The goal is working two-upstream aggregation.

### 2. Support GitHub HTTP MCP upstream

GitHub endpoint:

```text
https://api.githubcopilot.com/mcp/
```

GitHub authorization:

```text
Authorization: Bearer <token>
```

The token must come from environment variable or local uncommitted config only.

Recommended environment variable:

```text
GITHUB_MCP_TOKEN
```

Optional additional config variables:

```text
GITHUB_MCP_ENABLED=true
GITHUB_MCP_URL=https://api.githubcopilot.com/mcp/
GITHUB_MCP_TOOL_PREFIX=github/
GITHUB_MCP_CONNECT_TIMEOUT_MS=5000
GITHUB_MCP_RESPONSE_TIMEOUT_MS=60000
```

If the token is missing, Pyloros must still start. The GitHub provider may be disabled or report unavailable, but it must not break IntelliJ or native tools.

### 3. Register providers

The app should register at least:

```text
native/pyloros provider
intellij provider
github provider when enabled/configured
```

### 4. ToolCatalog behavior

The ToolCatalog must aggregate tools from all available providers.

Rules:

```text
- IntelliJ tools keep the intellij/ namespace.
- GitHub tools use the github/ namespace.
- Native Pyloros tools keep existing names, including pyloros__ping.
- Collisions must fail clearly; no silent overwrite.
- One unavailable provider must not remove all tools from other providers.
```

### 5. Tool call routing

`tools/call` must route based on the ToolCatalog entry:

```text
intellij/get_project_modules -> IntelliJ MCP upstream
github/<tool>                -> GitHub MCP upstream
pyloros__ping                -> native provider
```

### 6. Transport compatibility

IntelliJ MCP currently uses local SSE/message flow.
GitHub MCP may use streamable HTTP or HTTP MCP behavior.

Implement the smallest compatible transport needed for GitHub MCP. Do not break IntelliJ. If GitHub transport requires a separate code path, keep it isolated behind the generic MCP upstream abstraction.

### 7. Logging

Log provider lifecycle and tool discovery without secrets:

```text
[MCP-UPSTREAM] provider=github connecting url=https://api.githubcopilot.com/mcp/
[MCP-UPSTREAM] provider=github tools/list returned N tools
[MCP-UPSTREAM] provider=github unavailable reason=...
```

Never log Authorization header values or tokens.

## Configuration

Do not commit secrets.

Recommended local PowerShell setup:

```powershell
$env:GITHUB_MCP_TOKEN = '<real token here>'
$env:GITHUB_MCP_ENABLED = 'true'
```

If an example config is added, use only placeholders and default GitHub disabled:

```properties
github.mcp.enabled=false
github.mcp.url=https://api.githubcopilot.com/mcp/
github.mcp.tool.prefix=github/
github.mcp.token=
```

Prefer env for token.

## Acceptance Criteria

- `gradlew.bat clean build` is green.
- `gradlew.bat :pyloros-app:shadowJar` is green.
- Fat JAR still exists at `pyloros-app/build/libs/pyloros.jar`.
- Starting the fat JAR still works:

```powershell
& "C:\Program Files\Zulu\zulu-21\bin\java.exe" -jar .\pyloros-app\build\libs\pyloros.jar
```

- `GET http://127.0.0.1:8081/health` returns `200 {"status":"ok"}`.
- With GitHub token missing, Pyloros still starts and IntelliJ/native tools still work.
- With GitHub token configured, `tools/list` contains:

```text
pyloros__ping
intellij/get_project_modules
at least one github/... tool
```

- `tools/call intellij/get_project_modules` still works.
- At least one safe/read-only GitHub tool call works, if an appropriate tool exists in the GitHub MCP catalog.
- No token values are logged.
- No runtime data or secrets are committed.
- No ACP implementation is added.
- No Maven Central publish is attempted.

## Verification Notes

Use a safe GitHub tool for runtime verification. Prefer read-only tools such as listing/searching repositories, reading current user, or listing issues, depending on the GitHub MCP tool catalog returned.

If no safe GitHub tool can be called due to token permissions, still verify that `tools/list` includes `github/...` tools and document the permission limitation clearly.

## Not Allowed

- No ACP implementation.
- No virtual provider implementation.
- No GitHub token in committed files.
- No token logging.
- No OAuth behavior changes for the public ChatGPT -> Pyloros connection.
- No changes that break IntelliJ forwarding.
- No Maven Central publishing.
- No broad rewrite beyond what is needed to prove two MCP upstreams.

## Commit Message

`Add GitHub MCP upstream aggregation`

## Final Report

Overwrite `docs/agent/report.md` completely.

Include:

- Files changed
- New classes/interfaces introduced
- Whether the MCP upstream abstraction was generalized
- GitHub config keys used
- Build result
- Shadow JAR result
- Healthcheck result
- tools/list aggregation result, including counts per provider if available
- IntelliJ tool call verification
- GitHub tool list verification
- GitHub tool call verification or clear reason why it could not be called
- Exact commit hash
- Whether push was performed
