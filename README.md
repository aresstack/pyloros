# Pyloros

Pyloros is a headless Java 21 / Vert.x agent capability gateway that exposes local and remote MCP servers behind one MCP endpoint.

## What Pyloros does

- loads MCP upstreams from a Copilot-style `mcp.json`
- aggregates tools from multiple upstreams into one MCP catalog
- keeps canonical MCP tool names as `provider/tool`
- routes `tools/call` by exact tool name through a map-backed catalog
- keeps optional upstreams non-fatal so Pyloros can still start with native tools only

## Canonical tool naming

External MCP tool names remain canonical slash names:
- `intellij/get_project_modules`
- `github/get_me`
- `intellij-index/ide_index_status`

Important:
- slash is intentionally preserved
- slash is part of the MCP tool name
- slash is **not** treated as a resource hierarchy separator
- Pyloros routes exact tool names, not path fragments

Native tools may intentionally keep their own names, for example:
- `pyloros__ping`

## Transport matrix

| Provider | Transport |
| --- | --- |
| `intellij` | SSE |
| `github` | Streamable HTTP |
| `intellij-index` | Streamable HTTP |
| `pyloros` | native/local |

## Upstream configuration: `mcp.json`

Pyloros reads the same Copilot-style `mcp.json` that Copilot IntelliJ uses.

### Default discovery path

Default discovery order:
1. `--mcp-config=C:\path\to\mcp.json`
2. `-Dmcp.config=C:\path\to\mcp.json`
3. `%LOCALAPPDATA%\github-copilot\intellij\mcp.json`
4. `./mcp.json`
5. `./data/mcp.json`

On Angelo's machine the default path is:
- `C:\Users\angel\AppData\Local\github-copilot\intellij\mcp.json`

If no file is found, Pyloros starts with native tools only and logs:
- `[MCP-CONFIG] no mcp.json found; starting without external MCP upstreams`

### Supported `mcp.json` format

```json
{
  "servers": {
    "github": {
      "type": "http",
      "url": "https://api.githubcopilot.com/mcp/",
      "requestInit": {
        "headers": {
          "Authorization": "Bearer <TOKEN>"
        }
      }
    },
    "intellij": {
      "type": "sse",
      "url": "http://127.0.0.1:64343/sse"
    },
    "intellij-index": {
      "url": "http://127.0.0.1:29170/index-mcp/streamable-http"
    }
  }
}
```

Rules:
- `servers.<key>` is the `providerId`
- `type=sse` -> SSE upstream client
- `type=http` -> Streamable HTTP upstream client
- missing `type` with `http://` or `https://` URL -> Streamable HTTP upstream client
- `requestInit.headers` is forwarded to the upstream
- header values are never logged
- if a server exists in `mcp.json`, it is considered configured
- if a server is missing from `mcp.json`, it does not exist for Pyloros
- unsupported server types such as stdio are ignored for Pyloros HTTP aggregation

## Secrets

Do not commit secrets.

Important:
- do not hardcode tokens into repository files
- keep real credentials in the local Copilot `mcp.json` or other local-only config
- Pyloros will forward configured headers but does not log header values

## Requirements

- Java 21
- Gradle wrapper included

## Build

```powershell
Set-Location 'C:\Projects\pyloros'
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean build --no-daemon
.\gradlew.bat :pyloros-app:shadowJar --no-daemon
```

Fat jar output:
- `pyloros-app\build\libs\pyloros.jar`

## Start Pyloros

### Start using default `mcp.json` discovery

```powershell
Set-Location 'C:\Projects\pyloros'
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:OAUTH_CLIENT_ID='<your-client-id>'
$env:OAUTH_CLIENT_SECRET='<your-client-secret>'
& "$env:JAVA_HOME\bin\java.exe" -jar .\pyloros-app\build\libs\pyloros.jar
```

### Start with an explicit `mcp.json` path via CLI

```powershell
Set-Location 'C:\Projects\pyloros'
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:OAUTH_CLIENT_ID='<your-client-id>'
$env:OAUTH_CLIENT_SECRET='<your-client-secret>'
& "$env:JAVA_HOME\bin\java.exe" -jar .\pyloros-app\build\libs\pyloros.jar --mcp-config=C:\path\to\mcp.json
```

### Start with an explicit `mcp.json` path via system property

```powershell
Set-Location 'C:\Projects\pyloros'
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:OAUTH_CLIENT_ID='<your-client-id>'
$env:OAUTH_CLIENT_SECRET='<your-client-secret>'
& "$env:JAVA_HOME\bin\java.exe" -Dmcp.config=C:\path\to\mcp.json -jar .\pyloros-app\build\libs\pyloros.jar
```

## Local health check

```powershell
Invoke-WebRequest http://127.0.0.1:8081/health | Select-Object -ExpandProperty Content
```

Expected:

```json
{"status":"ok"}
```

## Connector refresh after restart or config changes

When `mcp.json` changes or Pyloros restarts:
- restart Pyloros
- refresh or update the ChatGPT / Copilot connector view
- call `tools/list` again
- use the refreshed canonical slash names returned by Pyloros

## Local smoke test vs real ChatGPT / `api_tool` invocation

### Local smoke test

The local smoke test verifies only the local Pyloros router and local MCP endpoint behavior.

It checks exact tool names such as:
- `pyloros__ping`
- `intellij/get_project_modules`
- `github/get_me`
- `intellij-index/ide_index_status`

Run:

```powershell
Set-Location 'C:\Projects\pyloros'
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:PYLOROS_SMOKE_ACCESS_TOKEN='<pyloros access token>'
$env:PYLOROS_SMOKE_MCP_URL='http://127.0.0.1:8081/sse'
.\gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

### Real ChatGPT / `api_tool` invocation test

This is separate from the local smoke test.

A real ChatGPT / connector invocation test validates the external client layer as well. The local smoke test does **not** prove that the ChatGPT connector or `api_tool` layer is correct.

## Architecture

- `PylorosApplication`: bootstrap and wiring only
- `ProviderRegistry`: deterministic provider registration by `providerId`
- `ToolCatalog`: immutable map-backed snapshots
- `ToolRouter`: exact-name routing by canonical MCP tool name
- `OAuthService`: OAuth token handling for the public Pyloros endpoint
- external upstreams are discovered from `mcp.json`
