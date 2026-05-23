# Current Assignment

Task: 009-E - Replace fragile ToolProvider routing with map-backed ToolCatalog routing

Read AGENTS.md first. Use this file as the single source of truth.

## Problem

The current ToolProvider / ToolCatalog implementation is too fragile. Aggregated tool names with slash are visible in `tools/list`, but invocation is still vulnerable to prefix / path parsing logic.

Tool names are MCP tool names, not resource paths.

## Architecture decision

- `provider/tool` remains the canonical external MCP tool name.
- No renaming to `provider__tool`.
- Slash is part of the MCP tool name, not a resource hierarchy separator.

## Goal

Replace tool routing with stable map-based routing built from catalog snapshots.

## Implementation

### 1. ToolCatalogSnapshot

Introduce or stabilize immutable snapshot data:
- `Map<String, ToolCatalogEntry> toolsByExternalName`
- `Map<ToolAddress, ToolCatalogEntry> toolsByAddress`
- optional `Map<String, List<ToolCatalogEntry>> toolsByProviderId`

Snapshot replacement must be atomic.

### 2. ToolAddress

`ToolAddress` must contain:
- `providerId`
- `upstreamToolName`

It is a value object. No string splitting logic in router.

### 3. ToolCatalogEntry

`ToolCatalogEntry` must contain:
- `externalName` (example: `github/get_me`)
- `address` (example: `providerId=github`, `upstreamToolName=get_me`)
- original MCP tool definition / schema
- optional provider metadata if needed

### 4. ProviderRegistry

Provider registry must be map-backed:
- `Map<String, ToolProvider> providersById`
- duplicate `providerId` must fail fast
- registration must be stable and deterministic

### 5. ToolCatalog refresh

For each provider:
- call `tools/list`
- form external name exactly once:
  - `externalName = providerId + "/" + upstreamToolName`
- store entry in snapshot maps
- duplicate external names must fail fast
- replace snapshot atomically

Native tools that intentionally do not use slash (for example `pyloros__ping`) may stay as-is, but must still be represented by stable catalog entries without router string parsing.

### 6. ToolRouter

`tools/call` routing must work like this:
- read `request.params.name` as exact string
- `entry = toolsByExternalName.get(name)`
- if missing: clean MCP tool-not-found style error
- provider lookup by `entry.address.providerId`
- provider call with `entry.address.upstreamToolName`
- no `split("/")`
- no `startsWith()`
- no resource path thinking

### 7. ToolProvider contract

Provider must be dumb:
- `listTools()` returns upstream tool definitions with upstream names
- `callTool(upstreamToolName, args)`
- provider does not know or parse external prefix names

### 8. Tests

Add automated tests for at least:
- `github/get_me` routes to `providerId=github`, `upstreamToolName=get_me`
- `intellij-index/ide_index_status` routes to `providerId=intellij-index`, `upstreamToolName=ide_index_status`
- `intellij/get_project_modules` routes to `providerId=intellij`, `upstreamToolName=get_project_modules`
- unknown tool name returns clean MCP error result
- duplicate external name fails fast
- duplicate provider id fails fast
- provider ids with hyphen work
- slash names stay visible in `tools/list`

### 9. Smoke test

Adapt smoke test so it continues to test slash-based names:
- `pyloros__ping`
- `intellij/get_project_modules`
- `github/get_me`
- `intellij-index/ide_index_status`

The smoke test may only prove that the local Pyloros router works locally. It must not claim that the ChatGPT tool layer works.

### 10. README

Update `README.md` to describe:
- Pyloros as headless agent capability gateway
- transport matrix:
  - `intellij = SSE`
  - `github = Streamable HTTP`
  - `intellij-index = Streamable HTTP`
- canonical tool naming:
  - `provider/tool`
  - slash intentionally preserved
- startup with upstream env vars
- connector refresh after restart
- difference between:
  - local smoke test
  - real ChatGPT / `api_tool` invocation test

## Acceptance

- `gradlew.bat clean build` green
- fat jar builds
- Pyloros starts with intellij, github, intellij-index
- `tools/list` contains slash names:
  - `intellij/get_project_modules`
  - `github/get_me`
  - `intellij-index/ide_index_status`
- `tools/call` via local MCP endpoint works with these exact slash names
- `README.md` updated
- `docs/agent/report.md` updated
- Commit only after explicit approval

## Not Allowed

- No ACP work
- No remote publish
- No secrets in files or report
