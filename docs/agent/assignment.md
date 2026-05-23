# Current Assignment

Task: 009-C - Local MCP Aggregation Smoke Test

Read AGENTS.md first. Use this file as the single source of truth.

## Goal

Implement a local smoke test for MCP aggregation that can run independently of the ChatGPT connector layer.
This verifies that Pyloros correctly routes and dispatches GitHub tools/call without relying on ChatGPT's toolcall layer.

## Scope

### 1. Smoke Test Implementation

Created: `pyloros-app/src/main/java/com/aresstack/pyloros/smoke/McpAggregationSmokeTest.java`

- Standalone Java CLI test class
- No JUnit, no embedded server
- Tests against a running Pyloros server
- Reads Bearer token from `PYLOROS_SMOKE_ACCESS_TOKEN` environment variable
- Reads MCP endpoint from `PYLOROS_SMOKE_MCP_URL` (default: http://127.0.0.1:8081/sse)

### 2. Test Sequence

1. `tools/list` – Aggregate all tools, count by provider
2. Validate: `pyloros__ping`, `intellij/get_project_modules`, at least 1 `github/*` tool
3. `tools/call pyloros__ping` – Native provider test
4. `tools/call intellij/get_project_modules` – IntelliJ upstream test
5. `tools/call github/<selected>` – GitHub upstream test
   - Auto-select read-only GitHub tool: `get_me`, `search_repositories`, etc.
   - Use safe arguments (e.g., topic:mcp for search)
6. Summary: total tools, GitHub count, test results

### 3. Gradle Integration

Added task to `pyloros-app/build.gradle`:

```bash
gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

Environment variables (passed through from shell):
- `PYLOROS_SMOKE_ACCESS_TOKEN` (required)
- `PYLOROS_SMOKE_MCP_URL` (optional, default: http://127.0.0.1:8081/sse)

### 4. Usage

Start Pyloros with a known access token:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$env:OAUTH_ACCESS_TOKEN = 'test-token-xyz'
$env:GITHUB_MCP_ENABLED = 'true'
$env:GITHUB_MCP_TOKEN = '<your-github-token>'

# Option A: Via fat JAR
java -jar .\pyloros-app\build\libs\pyloros.jar

# Option B: Via Gradle run
.\gradlew.bat :pyloros-app:run
```

Then in another terminal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$env:PYLOROS_SMOKE_ACCESS_TOKEN = 'test-token-xyz'
.\gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
```

## Acceptance

- `gradlew.bat clean build` is green
- Smoke test runs and reports all providers
- GitHub tool call is tested independently of ChatGPT connector
- No token values logged in output
- No secrets committed
- Report updated with results

## Notes on OAuth Token

The smoke test requires a Bearer token that Pyloros accepts. Options:

1. **Quickest (for testing):** Start Pyloros with `OAUTH_ACCESS_TOKEN=<value>`
   - This is a fixed token that the server will accept on every request
   - Set before starting the server, e.g., `$env:OAUTH_ACCESS_TOKEN='test-token-abc123'`

2. **Via OAuth flow (production):**
   - POST `/oauth/authorize` with PKCE → redirect with code
   - POST `/oauth/token` with code → get access_token
   - Use access_token in Bearer header

For 009-C, use option 1 (fixed token) for simplicity.

## Report

Overwrite `docs/agent/report.md` completely with:

- Smoke test implementation details
- Test sequence results
- GitHub tool selection and call result
- Tool counts per provider
- Exact Gradle command used
- Exact environment setup
- Build result: successful
- Result: successful or failed
- If failed: token issues or other errors

---

## Not Allowed

- No code changes to Pyloros core (test-only code)
- No remote publish
- No ACP implementation
- No token values in output or committed files
