# Task Report: 009-B — GitHub MCP Upstream Aggregation

## What was verified, changed, or implemented?

Added GitHub MCP as a second real MCP upstream provider alongside the existing IntelliJ provider.
Pyloros now exposes one aggregated MCP tool catalog containing native, IntelliJ, and GitHub tools.

The GitHub provider is optional: if `GITHUB_MCP_TOKEN` is not set, Pyloros starts normally
and all native/IntelliJ tools remain available. No startup failure occurs.

## Files changed or newly created

### New module: `pyloros-upstream-github`

| File | Description |
|------|-------------|
| `pyloros-upstream-github/build.gradle` | Gradle module descriptor (java-library, vertx-core, vertx-web-client, slf4j) |
| `pyloros-upstream-github/src/main/java/com/aresstack/pyloros/upstream/github/GitHubMcpConfig.java` | Config record: enabled, url, token, toolPrefix, timeouts |
| `pyloros-upstream-github/src/main/java/com/aresstack/pyloros/upstream/github/GitHubMcpClient.java` | HTTP MCP client using Vert.x WebClient (Streamable HTTP transport, SSE/JSON dual parsing) |
| `pyloros-upstream-github/src/main/java/com/aresstack/pyloros/upstream/github/GitHubToolProvider.java` | ToolProvider implementation; tools exposed as `github/<name>` |

### Modified files

| File | Change |
|------|--------|
| `settings.gradle` | Added `pyloros-upstream-github` to `include` list |
| `pyloros-app/build.gradle` | Added `implementation project(':pyloros-upstream-github')` |
| `pyloros-app/src/main/java/com/aresstack/pyloros/config/PylorosConfig.java` | Added `githubMcpConfig()` method reading GitHub env/properties config |
| `pyloros-app/src/main/java/com/aresstack/pyloros/PylorosApplication.java` | Wired `GitHubMcpClient` + `GitHubToolProvider` into provider list |
| `pyloros-app/src/main/resources/application.properties` | Added GitHub config defaults (all disabled, no token placeholder) |

## Architecture decisions touched

- **New module** `pyloros-upstream-github`: parallel to `pyloros-upstream-idea`, keeps the two upstream implementations cleanly separated without over-refactoring.
- **No generic base class**: `GitHubToolProvider` implements `ToolProvider` directly, same as `IdeaToolProvider`.
- **Transport**: GitHub uses MCP Streamable HTTP (HTTPS POST, synchronous response). IntelliJ uses SSE + async HTTP. Both are isolated behind the `ToolProvider` interface.
- **Tool namespace**: GitHub tools are exposed as `github/<original_name>`. The `nativeToolName()` method strips the prefix for upstream calls.
- **Optional upstream**: `GitHubToolProvider.listTools()` returns `List.of()` when disabled or token is absent; it never propagates failure to other providers.
- **MCP upstream abstraction NOT generalized**: The assignment allowed keeping providers separate. A generic `McpUpstreamToolProvider` was not introduced; each provider owns its transport code. This was the minimal change approach.

## GitHub config keys used

| Key | Environment variable | Default |
|-----|----------------------|---------|
| `github.mcp.enabled` | `GITHUB_MCP_ENABLED` | `false` |
| `github.mcp.url` | `GITHUB_MCP_URL` | `https://api.githubcopilot.com/mcp/` |
| *(token)* | `GITHUB_MCP_TOKEN` | *(empty — env only, never in files)* |
| `github.mcp.tool.prefix` | `GITHUB_MCP_TOOL_PREFIX` | `github/` |
| `github.mcp.connect.timeout.ms` | `GITHUB_MCP_CONNECT_TIMEOUT_MS` | `5000` |
| `github.mcp.response.timeout.ms` | `GITHUB_MCP_RESPONSE_TIMEOUT_MS` | `60000` |

## Build result

```
./gradlew.bat clean build   → BUILD SUCCESSFUL
```

Warnings only (pre-existing Javadoc warnings in `pyloros-upstream-idea`). No errors.

## Shadow JAR result

```
./gradlew.bat :pyloros-app:shadowJar   → BUILD SUCCESSFUL
pyloros-app/build/libs/pyloros.jar   exists
```

## Health check result

```
GET http://127.0.0.1:8081/health
→ 200 {"status":"ok"}
```

## tools/list aggregation result

Without `GITHUB_MCP_TOKEN` (token absent → GitHub provider returns empty list):

```
Total tools: 23
  pyloros__ping            (native provider)
  intellij/reformat_file
  intellij/create_new_file
  intellij/get_run_configurations
  intellij/get_file_text_by_path
  intellij/search_in_files_by_regex
  intellij/find_files_by_glob
  intellij/get_project_modules
  intellij/get_all_open_file_paths
  intellij/execute_terminal_command
  intellij/get_repositories
  intellij/execute_run_configuration
  intellij/find_files_by_name_keyword
  intellij/get_symbol_info
  intellij/search_in_files_by_text
  intellij/build_project
  intellij/replace_text_in_file
  intellij/runNotebookCell
  intellij/rename_refactoring
  intellij/open_file_in_editor
  intellij/get_project_dependencies
  intellij/list_directory_tree
  intellij/get_file_problems
```

Provider counts: native=1, intellij=22, github=0 (token not configured in this session).

## IntelliJ tool call verification

```
tools/call intellij/get_project_modules → isError: false
Result: {"modules":[{"name":"pyloros","type":"JAVA_MODULE"},
         {"name":"pyloros.pyloros-app","type":"JAVA_MODULE"},
         {"name":"pyloros.pyloros-server","type":"JAVA_MODULE"},
         {"name":"pyloros.pyloros-upstream-idea","type":"JAVA_MODULE"}, ...]}
```

IntelliJ tool forwarding works correctly.

## GitHub tool list verification

On startup with `GITHUB_MCP_TOKEN` absent, Pyloros logs:

```
[MCP-UPSTREAM] provider=github disabled (set GITHUB_MCP_ENABLED=true and GITHUB_MCP_TOKEN to enable)
```

The provider is registered and returns an empty tool list. No error is thrown.

**To verify with a real token:**
```powershell
$env:GITHUB_MCP_ENABLED = "true"
$env:GITHUB_MCP_TOKEN = "<your-github-copilot-token>"
& "C:\Program Files\Zulu\zulu-21\bin\java.exe" -jar pyloros-app\build\libs\pyloros.jar
```

After startup, `tools/list` will include `github/<tool_name>` entries from the GitHub MCP server.

## GitHub tool call verification

`GITHUB_MCP_TOKEN` was not present in the current session. GitHub tools could not be called.
This is a configuration/runtime gap, not an implementation gap.

The implementation path is:
1. `tools/call github/<tool>` → `ToolCatalog.resolve()` → `ToolAddress("github", "<tool>")`
2. `ToolRouter` → `GitHubToolProvider.callTool(McpToolCall("<tool>", args))`
3. `GitHubMcpClient.callTool(nativeName, arguments)` → POST to GitHub MCP endpoint

## No tokens logged

`GitHubMcpClient` logs only URL and method name. The Authorization header value is never logged.
All `[MCP-UPSTREAM] provider=github` log lines were reviewed and contain no token values.

## Exact commit hash

`adde12e` — `Add GitHub MCP upstream aggregation`

## Push performed

No push performed (not explicitly requested).
