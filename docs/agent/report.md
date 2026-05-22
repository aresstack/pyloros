# Task 002-I Report - Refresh Token Rotation Replay Tolerance

## What was verified, changed, or implemented

Implemented refresh-token replay tolerance in `OAuthService` for rotation mode:

- Added a dedicated short-lived replay cache for successful `grant_type=refresh_token` exchanges.
- Replay cache entries are keyed by consumed refresh token and store:
  - `TokenResponse`
  - `clientId`
  - replay expiry (`now + 10s`)
- On refresh requests where the token is no longer present in active refresh-token storage, the service now checks replay cache before returning `invalid_grant`.
- Implemented behavior:
  - same old refresh token + same client + within 10s => returns same successful `TokenResponse`
  - cache miss / expired => unchanged `400 invalid_grant`
  - client mismatch on replay entry => unchanged `400 invalid_grant`
- Added required log lines:
  - `[OAUTH] refresh_token replay hit token=... clientId=...`
  - `[OAUTH] refresh_token replay miss token=... clientId=...`
  - `[OAUTH] refresh_token replay rejected reason=client_mismatch ...`
- Kept authorization-code replay logic intact.
- Kept token logging safe by using `shortValue(...)` only.

Also added focused tests for rotation disabled and enabled replay behavior.

## Files changed or created

- `src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
- `src/test/java/com/aresstack/pyloros/oauth/OAuthServiceRefreshReplayTest.java` (new)
- `build.gradle` (add JUnit Platform launcher runtime dependency for Gradle 9 test execution)

## Architecture decision touched

- OAuth replay tolerance stays in OAuth service layer.
- `OAuthService` remains owner of grant validation and replay handling.
- Authorization-code replay remains unchanged.
- Refresh-token replay tolerance is isolated as a separate cache path.

## Tests, builds, and runtime checks executed

Build/test command (Java 21):

- `./gradlew clean build` with `JAVA_HOME=C:\Program Files\Zulu\zulu-21`
- Result: **BUILD SUCCESSFUL**

Runtime verification (service-level, via JUnit during build):

- rotation=false:
  - repeated refresh requests with same refresh token remain valid
- rotation=true:
  - first refresh rotates token
  - duplicate refresh with old token within 10s returns same `TokenResponse`
  - duplicate refresh with old token after 10s returns `invalid_grant`

## Result

Successful

## If failed: exact error and recommended next step

No final failure.

Note: local-code-search subagent invocation failed in this environment due missing model `qwen2.5-coder:14b`; workspace analysis then proceeded with direct repository search/read tools.

## Exact commit hash

Code commit: `c46c083`

## Push status

Code commit push: performed (`main -> origin/main`)
