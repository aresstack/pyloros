# Report: Fix OAuth token exchange — ChatGPT reconnect fails with invalid_client

## What was verified, changed or implemented?

**Root cause**: ChatGPT sends no `client_secret` during `/oauth/token` exchange when using PKCE (OAuth 2.0 Public Client behavior). Pyloros `isKnownOAuthClient()` required BOTH `clientId` AND `clientSecret` to match config, but ChatGPT only sends `client_id=angel` (no secret). This caused `401 invalid_client`.

**Fix**: Split client authentication by grant type:
- `authorization_code` grant: only check `clientId` (PKCE + authorization code provide sufficient security; OAuth 2.0 allows public clients to skip client_secret)
- `refresh_token` grant: still check both `clientId` AND `clientSecret` via existing `isKnownOAuthClient()`
- Added debug logging in `readClientCredentials` (shows source: Basic Auth vs form params, and whether a secret was sent)

## Which files were changed or newly created?

- **Changed**: `pyloros-auth-oauth/src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
  - Lines 101-133: `exchangeAuthorizationCode` — split clientId-only check from full isKnownOAuthClient; isKnownOAuthClient only called for refresh_token
  - Added `hasSecret` to log line and configurable client_id mismatch warning

- **Changed**: `pyloros-auth-oauth/src/main/java/com/aresstack/pyloros/http/OAuthRoutes.java`
  - Lines 91-114: `readClientCredentials` — added INFO logging showing credential source and whether secret was present

## Which architecture decision was touched?

- OAuth client authentication: previously required client_secret for ALL grant types; now only requires it for `refresh_token`. This aligns with OAuth 2.0 RFC 6749 Section 2.3.1 which allows public clients to skip client authentication, and with PKCE (RFC 7636) which provides code_verifier-based security for authorization_code exchange.

## Which tests, builds and runtime checks were executed?

- `./gradlew :pyloros-auth-oauth:test` → SUCCESS (3.9s)
- Full project rebuild (`build_project` with rebuild=true) → SUCCESS (no errors)

## Result: successful or failed

- **Successful** — code change compiles, all existing OAuth tests pass

## If failed: exact error and recommended next step

- N/A

## Exact commit hash, or No commit created

- No commit created (no push requested)
