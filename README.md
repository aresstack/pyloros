# Pyloros

Headless Java 21 / Vert.x gateway for exposing local and remote MCP tool capabilities behind one MCP endpoint.

## What it is

Pyloros is a capability gateway:
- aggregates tools from multiple upstream providers;
- keeps `PylorosApplication` as bootstrap and wiring only;
- routes tool calls through `ToolProvider`, `ProviderRegistry`, `ToolCatalog`, and `ToolRouter`;
- tolerates optional upstream outages so startup is not blocked.

## Transport matrix

| Provider | Transport | Notes |
| --- | --- | --- |
| `intellij` | SSE | Local IntelliJ MCP bridge |
| `github` | Streamable HTTP | GitHub MCP upstream |
| `intellij-index` | Streamable HTTP | IntelliJ index MCP upstream |
| `pyloros` | local/native | Built-in gateway tools |

## Canonical tool naming

External MCP tool names are canonicalized as:
- `provider/tool`
- slash is intentionally preserved
- slash is part of the MCP tool name, not a resource path

Examples:
- `github/get_me`
- `intellij/get_project_modules`
- `intellij-index/ide_index_status`

Native tools may intentionally keep their own non-slash name, for example:
- `pyloros__ping`

## Requirements

- Java 21
- Gradle wrapper included

## Build

```powershell
Set-Location 'C:\Projects\pyloros'
.\gradlew.bat clean build
.\gradlew.bat :pyloros-app:shadowJar
```

Fat jar output:
- `pyloros-app\build\libs\pyloros.jar`

## Startup

Set the required OAuth variables and any upstream variables before starting.

### Minimum local start

```powershell
Set-Location 'C:\Projects\pyloros'

$env:OAUTH_CLIENT_ID = '<your-client-id>'
$env:OAUTH_CLIENT_SECRET = '<your-client-secret>'

java -jar .\pyloros-app\build\libs\pyloros.jar
```

### Start with upstreams enabled

```powershell
Set-Location 'C:\Projects\pyloros'

$env:OAUTH_CLIENT_ID = '<your-client-id>'
$env:OAUTH_CLIENT_SECRET = '<your-client-secret>'

$env:IDEA_MCP_ENABLED = 'true'
$env:IDEA_MCP_HOST = '127.0.0.1'
$env:IDEA_MCP_PORT = '64343'
$env:IDEA_MCP_SSE_PATH = '/sse'

$env:PYLOROS_UPSTREAM_GITHUB_ENABLED = 'true'
$env:GITHUB_MCP_TOKEN = '<real token>'
$env:PYLOROS_UPSTREAM_GITHUB_URL = 'https://api.githubcopilot.com/mcp/'

$env:PYLOROS_UPSTREAM_INTELLIJ_INDEX_ENABLED = 'true'
$env:PYLOROS_UPSTREAM_INTELLIJ_INDEX_URL = 'http://127.0.0.1:29170/index-mcp/streamable-http'

java -jar .\pyloros-app\build\libs\pyloros.jar
```

Relevant optional variables:
- `SERVER_PORT`
- `PUBLIC_ORIGIN`
- `MCP_PUBLIC_PATH`
- `IDEA_MCP_ENABLED`
- `IDEA_MCP_HOST`
- `IDEA_MCP_PORT`
- `IDEA_MCP_SSE_PATH`
- `PYLOROS_UPSTREAM_GITHUB_ENABLED`
- `PYLOROS_UPSTREAM_GITHUB_URL`
- `GITHUB_MCP_TOKEN`
- `PYLOROS_UPSTREAM_INTELLIJ_INDEX_ENABLED`
- `PYLOROS_UPSTREAM_INTELLIJ_INDEX_URL`

## Connector refresh behavior

Pyloros builds a tool catalog snapshot from upstream `tools/list` responses.

If an upstream connector or Pyloros itself restarts, or if an upstream tool set changes:
- restart or reconnect the upstream as needed;
- refresh or update the connector/client view after the restart;
- call `tools/list` again through Pyloros to refresh the aggregated snapshot;
- use the refreshed canonical tool names returned by Pyloros.

## Local checks

Health endpoint:

```powershell
Invoke-WebRequest http://127.0.0.1:8081/health | Select-Object -ExpandProperty Content
```

Expected response:

```json
{"status":"ok"}
```

## Smoke test vs real ChatGPT tool invocation

### Local smoke test

`McpAggregationSmokeTest` verifies the local Pyloros MCP router only:
- `tools/list` on the local Pyloros endpoint;
- exact tool names such as `pyloros__ping`, `intellij/get_project_modules`, `github/get_me`, `intellij-index/ide_index_status`;
- local `tools/call` routing through Pyloros.

Run:

```powershell
$env:PYLOROS_SMOKE_ACCESS_TOKEN = '<pyloros access token>'
$env:PYLOROS_SMOKE_MCP_URL = 'http://127.0.0.1:8081/sse'
.\gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

### Real ChatGPT / `api_tool` invocation test

This is separate from the local smoke test.
It requires a real ChatGPT tool integration path and should be treated as an end-to-end client validation, not as proof of local router correctness.

## Architecture

- `PylorosApplication`: bootstrap and wiring only
- `OAuthService`: OAuth token management
- `ProviderRegistry`: deterministic provider registration by provider id
- `ToolCatalog`: immutable catalog snapshots for external tool names and routed addresses
- `ToolRouter`: exact-name `tools/call` routing
- provider implementations: upstream-specific MCP connectors
