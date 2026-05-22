# 002 - OAuth Refresh Token and Reauthentication Handling

## Problem

The ChatGPT connector can enter a `Reauthentication required` state after some time. This indicates that the current OAuth integration is not yet complete for long-lived usage.

The current Pyloros OAuth flow issues short-lived access tokens, but does not implement refresh tokens or a graceful reauthentication strategy.

## Goal

Pyloros should support long-lived ChatGPT connector sessions without requiring unnecessary manual reauthentication.

## Scope

Implement OAuth refresh-token support for the ChatGPT-facing OAuth server.

## Requirements

- `/oauth/token` must support `grant_type=refresh_token`.
- The authorization-code exchange should return a `refresh_token` in addition to the access token.
- Refresh tokens must be stored server-side.
- Refresh tokens must be bound to the OAuth client id.
- Refresh tokens must be revocable by replacing or deleting them.
- Access tokens should remain short-lived.
- Refresh-token exchange should issue a new access token.
- Optionally rotate refresh tokens on every refresh.

## Suggested Token Response

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "scope": "mcp"
}
```

## Error Handling

If a refresh token is missing, invalid, expired, or bound to another client:

```json
{
  "error": "invalid_grant"
}
```

Pyloros must not crash when invalid refresh requests arrive.

## Configuration

Suggested properties:

```properties
oauth.access-token.ttl.seconds=3600
oauth.refresh-token.ttl.seconds=2592000
oauth.refresh-token.rotation.enabled=true
```

Suggested environment overrides:

```text
OAUTH_ACCESS_TOKEN_TTL_SECONDS
OAUTH_REFRESH_TOKEN_TTL_SECONDS
OAUTH_REFRESH_TOKEN_ROTATION_ENABLED
```

## Acceptance Criteria

- Authorization-code token exchange returns `refresh_token`.
- `grant_type=refresh_token` works.
- A refreshed access token is accepted by `/sse` and MCP POST requests.
- Invalid refresh tokens return `invalid_grant`.
- Build is green.
- Existing authorization-code flow remains compatible with ChatGPT.

## Not in Scope

- Persistent database storage.
- Multi-user account management.
- OAuth device flow.
- External identity provider integration.
