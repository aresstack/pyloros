# Pyloros

Reactive Java / Vert.x gateway guarding and routing MCP tool access for AI-assisted development.

## Features

- **OAuth 2.0 with Refresh Token Rotation**: Secure token handling with automatic refresh and optional rotation
- **MCP Tool Forwarding**: Route and guard access to multiple MCP upstream providers (IntelliJ IDEA, etc.)
- **Async HTTP Backend**: Built on Vert.x for high-performance async I/O
- **Tool Prefix Isolation**: Namespace tools from different upstreams (e.g., `idea__get_all_open_file_paths`)

## Requirements

- Java 21+ (tested with Zulu)
- Gradle (wrapper included)

## Starten / Getting Started

```ps1
Set-Location 'C:\Projects\pyloros'

$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Server configuration
$env:SERVER_PORT = '64343'
$env:PUBLIC_ORIGIN = 'https://<your-domain>'

# OAuth configuration (get credentials from your OAuth provider)
$env:OAUTH_CLIENT_ID = '<your-client-id>'
$env:OAUTH_CLIENT_SECRET = '<your-client-secret>'

# Optional: IntelliJ IDEA upstream
$env:IDEA_TOOL_PREFIX = 'idea'

.\gradlew.bat --no-daemon run --stacktrace
```

### Optional: Configure short TTLs

For testing:
```ps1
$env:OAUTH_ACCESS_TOKEN_TTL_SECONDS="300"
$env:OAUTH_REFRESH_TOKEN_TTL_SECONDS="3600"
$env:OAUTH_REFRESH_TOKEN_ROTATION_ENABLED="false"
```
For production:
```ps1
$env:OAUTH_ACCESS_TOKEN_TTL_SECONDS="3600"
$env:OAUTH_REFRESH_TOKEN_TTL_SECONDS="2592000"
$env:OAUTH_REFRESH_TOKEN_ROTATION_ENABLED="true"
```

## Running Pyloros locally on Windows

Use the operator scripts in `scripts/` instead of setting environment variables manually.

**Requirements:** Java 21+ (e.g. Zulu 21 at `C:\Program Files\Zulu\zulu-21`)

> Pyloros listens on HTTP (`http://127.0.0.1:8081` by default).  
> HTTPS is terminated by Apache, which proxies to the HTTP backend.  
> Do not expose the HTTP port directly to the internet.

### Start

```ps1
.\scripts\start-pyloros.ps1
```

Optional: override port:

```ps1
.\scripts\start-pyloros.ps1 -Port 8082
```

Set secrets before starting (never hardcoded in the script):

```ps1
$env:OAUTH_CLIENT_ID     = '<your-client-id>'
$env:OAUTH_CLIENT_SECRET = '<your-client-secret>'
.\scripts\start-pyloros.ps1
```

### Stop

```ps1
.\scripts\stop-pyloros.ps1
# or with immediate kill:
.\scripts\stop-pyloros.ps1 -Force
```

### Check status

```ps1
.\scripts\check-pyloros.ps1
```

Performs a port check and an HTTP call to `GET /health` → `{"status":"ok"}`.

---

## Architecture

- `PylorosApplication`: Bootstrap and Vert.x wiring only
- `OAuthService`: Token management, refresh, and rotation
- `IdeaToolProvider`: MCP tool forwarding to IntelliJ IDEA upstream
- `ToolRegistry` + `ToolProvider`: Tool aggregation from multiple upstreams
- `McpRoutes`, `OAuthRoutes`, `MetadataRoutes`: HTTP endpoint handlers

## Development

Build and test:
```ps1
.\gradlew.bat clean build
.\gradlew.bat --no-daemon run --stacktrace
```

## Status

- OAuth refresh token handling: ✅ implemented (002-A … 002-I)
- IDEA tool forwarding: ✅ implemented
- HTTP backend: ✅ implemented
- .gitignore: ✅ configured
- Windows operator scripts: ✅ scripts/start-pyloros.ps1, stop-pyloros.ps1, check-pyloros.ps1
- RFC 6750 auth failure signalling: ✅ implemented (002-H)
