# Current Assignment

Task: 002-F - OAuth authorization code replay tolerance

Read AGENTS.md first. Use this file as the single source of truth.

Goal: Tolerate duplicate parallel authorization_code token requests from the same connector
(observed: same code exchanged twice within 16 ms → second request gets invalid_grant).

Scope:
1. Add replay cache in OAuthService (10-second TTL)
2. On first successful authorization_code exchange: store TokenResponse in replay cache
3. On second authorization_code exchange (code already consumed):
   - Check replay cache by code
   - If same client_id + redirect_uri + code_verifier → return cached TokenResponse
   - If different client/redirect/verifier → throw invalid_grant (security unchanged)
   - If replay cache entry expired → throw invalid_grant
4. Add cleanupExpiredReplayCache() called on each exchange
5. Add Pragma: no-cache to HttpJson.send() (global, for error responses)
6. Log replay hits and misses without token values
7. Execute full Gradle build
8. Commit and push

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
- 002-E (diagnostics + Pragma token response): Commit 1ec7d33
- 003-A/B: Commit c7e068c
- 005: Commit a5fee45

Final report:
Overwrite docs/agent/report.md completely.
Include files changed, replay cache logic description, build results, commit hash.
