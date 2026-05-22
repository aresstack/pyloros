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

### Optional: Configure short TTLs for testing

```ps1
$env:OAUTH_ACCESS_TOKEN_TTL_SECONDS="30"
$env:OAUTH_REFRESH_TOKEN_TTL_SECONDS="300"
$env:OAUTH_REFRESH_TOKEN_ROTATION_ENABLED="false"
```

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

Release baseline: 005 - Repository hygiene and release baseline

- OAuth refresh token handling: ✅ implemented
- IDEA tool forwarding: ✅ implemented
- HTTP backend: ✅ implemented
- .gitignore: ✅ configured

