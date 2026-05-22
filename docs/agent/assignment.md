# Current Assignment

Task: 006 - Runtime Packaging and Operator Scripts

Read AGENTS.md first. Use this file as the single source of truth.

## Context

The OAuth and connector stabilization work is complete:

- 002-A: OAuth refresh token support
- 002-B: Refresh token expiration cleanup
- 002-C: Refresh response compatibility
- 002-D: Persistent refresh token store
- 002-E: OAuth token response diagnostics / RFC headers
- 002-F: Authorization-code replay tolerance
- 002-G: Refresh rejection diagnostics
- 002-H: RFC 6750 auth failure signalling
- 002-I: Refresh-token rotation replay tolerance
- 003-A/B: IDEA upstream restart resilience
- 005: Repository hygiene and release baseline

The public connector works with normal TTL values:

- Access token TTL: 3600 seconds
- Refresh token TTL: 2592000 seconds
- Optional refresh-token rotation works with replay tolerance

Current operational problem:

Pyloros is still usually started manually through Gradle and PowerShell environment variables. This is error-prone. We need small operator scripts for local/runtime operation.

## Goal

Create simple Windows PowerShell operator scripts that make Pyloros easy to start, stop, and check without manually typing environment variables every time.

## Scope

Create a new directory:

```text
scripts/
```

Add these scripts:

```text
scripts/start-pyloros.ps1
scripts/stop-pyloros.ps1
scripts/check-pyloros.ps1
```

### start-pyloros.ps1

Responsibilities:

1. Resolve the project root relative to the script location.
2. Set a stable `JAVA_HOME` default if not already set:
   - Prefer `C:\Program Files\Zulu\zulu-21` when present.
   - If `JAVA_HOME` is already set, do not overwrite it.
3. Ensure Java 21+ is used.
4. Set safe runtime defaults unless the environment already defines them:
   - `SERVER_PORT=8081`
   - `PUBLIC_ORIGIN=https://current-car.com`
   - `OAUTH_ACCESS_TOKEN_TTL_SECONDS=3600`
   - `OAUTH_REFRESH_TOKEN_TTL_SECONDS=2592000`
   - `OAUTH_REFRESH_TOKEN_ROTATION_ENABLED=false`
5. Do not hardcode secrets.
6. Require `OAUTH_CLIENT_ID` and `OAUTH_CLIENT_SECRET` only if the current application configuration needs them externally. If the project still uses application.properties defaults, do not break that behavior.
7. Start Pyloros with Gradle wrapper:
   - `./gradlew.bat --no-daemon run --stacktrace`
8. Write a short startup banner showing:
   - project root
   - Java version
   - server port
   - public origin
   - refresh token store path if configured or defaulted
9. Do not write tokens or secrets to the console.

### stop-pyloros.ps1

Responsibilities:

1. Find the process listening on the configured/default port `8081`.
2. Show PID and process name.
3. Stop only that process.
4. If no process is listening on the port, print a friendly message and exit successfully.
5. Provide an optional `-Force` switch.
6. Avoid killing unrelated Java processes unless they are actually bound to the target port.

### check-pyloros.ps1

Responsibilities:

1. Check whether a process is listening on the configured/default port `8081`.
2. Show PID, process name, and local address.
3. Optionally perform a lightweight HTTP check if a suitable endpoint exists.
4. Do not require OAuth credentials.
5. If no health endpoint exists yet, just report port/process status and mention that no dedicated health endpoint is currently available.

## Optional but useful

If small and safe, add a simple unauthenticated health endpoint:

```text
GET /health
```

Expected response:

```json
{"status":"ok"}
```

If implemented, update `check-pyloros.ps1` to call it.

Do not make this endpoint expose secrets, token counts, upstream tool lists, or internal state.

## Documentation

Update `README.md` with a short section:

```text
Running Pyloros locally on Windows
```

Include:

- Start command
- Stop command
- Check command
- Note that Apache should proxy to HTTP backend `http://127.0.0.1:8081`
- Note that HTTPS is terminated by Apache in the current deployment
- Note that Java 21+ is required

## Acceptance Criteria

- `scripts/start-pyloros.ps1` exists and starts Pyloros successfully.
- `scripts/stop-pyloros.ps1` exists and can stop the process bound to port 8081.
- `scripts/check-pyloros.ps1` exists and reports current status.
- Build is green.
- Existing OAuth behavior remains unchanged.
- Existing MCP tools/list and `intellij/get_project_modules` continue to work after starting via the script.
- No secrets are printed.
- No generated runtime data is committed.

## Not Allowed

- No Windows service installation yet.
- No NSSM setup yet.
- No Docker setup.
- No Linux/systemd setup.
- No HTTPS listener implementation.
- No broad refactoring.
- No changes to OAuth semantics unless required for a health endpoint, which should not be OAuth-protected.

## Commit Message

`Add Windows operator scripts`

## Final Report

Overwrite `docs/agent/report.md` completely.

Include:

- Files changed
- Whether `/health` was added or intentionally skipped
- Exact start/check/stop commands tested
- Build result
- Runtime verification result
- Exact commit hash
- Whether push was performed
