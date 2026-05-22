# Current Assignment

Task: 002-G - Refresh token rejection diagnostics

Read AGENTS.md first. Use this file as the single source of truth.

Goal: Log the internal reason for refresh_token invalid_grant without exposing token values.
Improve startup log for refresh token store with expired count.

Scope:
1. exchangeFromRefreshToken() - log internal reason before throwing invalid_grant:
   - missing_refresh_token (null or blank)
   - unknown_refresh_token (not in map)
   - expired_refresh_token (found but expired)
   - client_mismatch (clientId does not match)
2. loadRefreshTokensFromStore() - skip expired tokens during load, log summary:
   loaded=X, expired_removed=Y, active=Z
3. External response stays: {"error":"invalid_grant"} (no reason exposed)
4. No token values in log - use shortValue() helper
5. Execute full Gradle build
6. Commit and push

Not allowed:
- No token values in log output
- No broad code refactoring
- No new features beyond this scope

Status of previous tasks (all completed):
- 001-A bis 001-E: implemented
- 002-A: Commit 7466f86
- 002-B: Commit 1ad0303
- 002-C: Commit 921c002
- 002-D: Commit f7ececf
- 002-E (diagnostics + Pragma): Commit 1ec7d33
- 002-F (replay tolerance): Commit 9d9423a
- 003-A/B: Commit c7e068c
- 005: Commit a5fee45

Final report:
Overwrite docs/agent/report.md completely.
Include files changed, log examples, build results, commit hash.
