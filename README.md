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
- `--tool-name-separator=/`

## Architecture

- `pyloros-server`: core MCP gateway and provider/tool aggregation logic
- `pyloros-app`: runtime bootstrap for the Pyloros gateway process
- `pyloros-manager-agent`: standalone Java 21 manager-agent bootstrap module shipped as a Gradle application distribution/start script, with no core dependency from server/app
  - stdio safety: logging is configured to `stderr` so ACP JSON-RPC `stdout` stays clean
- `PylorosApplication`: bootstrap and wiring only
- `ProviderRegistry`: deterministic provider registration by `providerId`
- `ToolCatalog`: immutable map-backed snapshots
- `ToolRouter`: exact-name routing by external MCP tool name
- `OAuthService`: OAuth token handling for the public Pyloros endpoint
- external upstreams are discovered from `mcp.json`

## Build

```bash
./gradlew build
```

## Run

```bash
./gradlew :pyloros-app:run
```

## Manager agent application distribution

The R6 manager-agent scaffold is intentionally a separate Gradle `application` module, not a fat JAR.
Use the generated application distribution/start scripts or the Gradle run task during development:

```bash
./gradlew :pyloros-manager-agent:installDist
./pyloros-manager-agent/build/install/pyloros-manager-agent/bin/pyloros-manager-agent

# or during development
./gradlew :pyloros-manager-agent:run
```

A future release can add an executable or fat JAR if required by packaging policy.
