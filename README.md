# Pyloros

Pyloros is a headless Java 21 / Vert.x agent capability gateway that exposes local and remote MCP servers behind one MCP endpoint.

Canonical public endpoint:
- `https://current-car.com/pyloros`

Legacy compatibility alias:
- `https://current-car.com/sse` (deprecated)

## What Pyloros does

- loads MCP upstreams from a Copilot-style `mcp.json`
- aggregates tools from multiple upstreams into one MCP catalog
- formats external MCP tool names as `provider<separator>tool` (default separator `__`)
- routes `tools/call` by exact tool name through a map-backed catalog
- keeps optional upstreams non-fatal so Pyloros can still start with native tools only

## External tool naming

Default external MCP tool names use `__`:
- `intellij__get_project_modules`
- `github__get_me`
- `intellij-index__ide_index_status`

Override priority for separator:
1. `--tool-name-separator=...`
2. `-Dmcp.tool-name-separator=...`
3. default `__`

Example compatibility/test mode:
- `--tool-name-separator=/` (or `-Dmcp.tool-name-separator=/`)
- yields names like `intellij/get_project_modules`
- `/` mode is intended for test/compatibility only

Important:
- external names are still routed by exact string match only
- internal routing address remains `ToolAddress(providerId, upstreamToolName)`
- no split/prefix/path-fragment routing is used

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
- use the refreshed exact names returned by Pyloros (`__` by default)

## Local smoke test vs real ChatGPT / `api_tool` invocation

### Local smoke test

The local smoke test verifies only the local Pyloros router and local MCP endpoint behavior.

It checks exact tool names such as:
- `pyloros__ping`
- `intellij__get_project_modules`
- `github__get_me`
- `intellij-index__ide_index_status`

Run:

```powershell
Set-Location 'C:\Projects\pyloros'
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:PYLOROS_SMOKE_ACCESS_TOKEN='<pyloros access token>'
$env:PYLOROS_SMOKE_MCP_URL='http://127.0.0.1:8081/pyloros'
.\gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

For legacy compatibility verification, override the smoke URL with `http://127.0.0.1:8081/sse`.

### Real ChatGPT / `api_tool` invocation test

This is separate from the local smoke test.

A real ChatGPT / connector invocation test validates the external client layer as well. The local smoke test does **not** prove that the ChatGPT connector or `api_tool` layer is correct.

## Architecture

- `pyloros-server`: core MCP gateway and provider/tool aggregation logic
- `pyloros-app`: runtime bootstrap for the Pyloros gateway process
- `pyloros-manager-agent`: separate Java 21 Gradle `application` bootstrap module (distribution/start script), independent from core server/app runtime
  - stdio safety: module-local logging is routed to `stderr` so ACP JSON-RPC `stdout` remains free
- `PylorosApplication`: bootstrap and wiring only
- `ProviderRegistry`: deterministic provider registration by `providerId`
- `ToolCatalog`: immutable map-backed snapshots
- `ToolRouter`: exact-name routing by external MCP tool name
- `OAuthService`: OAuth token handling for the public Pyloros endpoint
- external upstreams are discovered from `mcp.json`

### Manager-agent application distribution (R6 scaffold)

`pyloros-manager-agent` is currently packaged as a Gradle application distribution/start script (not a fat JAR):

```bash
./gradlew :pyloros-manager-agent:installDist
./pyloros-manager-agent/build/install/pyloros-manager-agent/bin/pyloros-manager-agent
```

## Plugin developer guide

- see `docs/plugin-development.md`
