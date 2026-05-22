# Current Assignment

Task: 002-H - Auth failure signalling (RFC 6750 Bearer Token Error Responses)

Read AGENTS.md first. Use this file as the single source of truth.

Goal: /sse and /sse POST return RFC 6750-compliant 401 responses with WWW-Authenticate
header and structured JSON so ChatGPT has the best possible signal to trigger reconnect.

Scope:
1. Add BearerAuthResult enum in domain/oauth/: OK, MISSING_TOKEN, INVALID_TOKEN, EXPIRED_TOKEN
2. Add checkBearerAuth(String authorizationHeader) in OAuthService returning BearerAuthResult
3. OAuthService: distinguish INVALID_TOKEN (unknown) vs EXPIRED_TOKEN in isBearerAuthorized area
4. McpRoutes: use checkBearerAuth() instead of isBearerAuthorized()
5. McpRoutes: unauthorized() now sends:
   - HTTP 401
   - WWW-Authenticate: Bearer error="invalid_token", error_description="The access token is invalid or expired"
   - Content-Type: application/json
   - Cache-Control: no-store
   - Pragma: no-cache
   - Body: {"error":"invalid_token"} for token present but invalid/expired
   - Body: {"error":"invalid_token"} for missing token (same external signal)
6. McpRoutes: log auth rejection:
   [MCP] auth rejected reason=missing_token
   [MCP] auth rejected reason=invalid_token
   [MCP] auth rejected reason=expired_token
7. No token values in log
8. /oauth/token invalid_grant stays 400 (unchanged)
9. Execute full Gradle build
10. Commit and push

Not allowed:
- No token values in log output
- No broad code refactoring

Status of previous tasks (all completed):
- 002-A: Commit 7466f86
- 002-B: Commit 1ad0303
- 002-C: Commit 921c002
- 002-D: Commit f7ececf
- 002-E (diagnostics): Commit 1ec7d33
- 002-F (replay tolerance): Commit 9d9423a
- 002-G (refresh diagnostics): Commit 70b3d07
- 003-A/B: Commit c7e068c
- 005: Commit a5fee45

Final report:
Overwrite docs/agent/report.md completely.
Include files changed, WWW-Authenticate examples, log examples, build result, commit hash.
