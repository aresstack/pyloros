# ACP Agent Authentication

## Overview

Pyloros recognizes that installed ACP registry agents may require authentication before they can operate. This document describes how auth requirements are modeled, detected, and handled.

## Auth Methods

| Method | Description | Pyloros Behavior |
|---|---|---|
| `NONE` | Agent requires no authentication | Normal install and provider registration |
| `AGENT_AUTH` | Agent handles its own browser/OAuth flow | Agent is installed with `CONFIGURED` status and provider-eligible; Pyloros stores no credentials and does not execute any auth; the agent manages its own login at startup |
| `TERMINAL_AUTH` | Agent requires a terminal setup command before use | Agent is installed with `REQUIRED` status; setup command is presented to the user |

## Lifecycle

```
Registry metadata → AgentLifecycleService.install()
                         ↓
              InstalledAgent (authMethod, authStatus, authSetupCommand)
                         ↓
              InstalledAgentAcpProviderFactory.createConfigurations()
                         ↓
              ┌─ authStatus == REQUIRED  → Failure (not registered as provider)
              └─ authStatus != REQUIRED  → Registered as ACP provider
```

## Auth Status

- **`NOT_REQUIRED`** — Agent needs no auth (default for `NONE` method)
- **`REQUIRED`** — Auth is needed but not yet configured. Agent is installed but **not** registered as a provider
- **`CONFIGURED`** — Auth has been set up. Agent is eligible for provider registration

## Terminal Auth Flow

1. Registry declares `auth.method = "TERMINAL_AUTH"` with a `setupCommand`
2. On install, the agent is persisted with `authStatus = REQUIRED`
3. When provider configurations are generated, the agent is **rejected** with a structured failure that includes the setup command
4. User runs the setup command in their terminal
5. After setup, user updates the agent's auth status to `CONFIGURED`
6. On next provider generation, the agent passes the auth check

## Agent Auth Flow

For agents using `AGENT_AUTH`:
1. Registry declares `auth.method = "AGENT_AUTH"` with a description
2. On install, the agent is persisted with `authStatus = CONFIGURED` (provider-eligible)
3. Pyloros does not intercept the agent's OAuth browser flow
4. Pyloros stores no credentials and does not execute any auth — the agent manages its own login at startup
5. No status update to `CONFIGURED` is needed since the agent is already provider-eligible

## Security Model

- **No credential storage**: Pyloros stores only metadata (method, status, setup command, description) — never passwords, tokens, or API keys
- **No automatic auth execution**: Terminal setup commands are presented to the user but never executed automatically
- **No credential logging**: Auth fields contain no secrets; all log sanitization rules apply
- **Auth metadata is not sensitive**: Method names, status values, setup commands, and descriptions are user-facing metadata

## Registry Validation

When loading the ACP registry:
- Auth is entirely optional — agents without an `auth` field default to `NONE`
- If `auth.method` is `TERMINAL_AUTH` but `auth.setupCommand` is blank, the registry entry is rejected as invalid
- Unknown auth fields are silently ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`)

## Limitations (R7)

- No generic credential/token safe — not in R7 scope
- No provider-specific auth hacks
- No automatic auth execution without user decision
- Auth status must be manually updated to `CONFIGURED` (future: auto-detect via agent handshake)

## Example: Auth-Aware Registry Entry

```json
{
  "id": "my-cli-agent",
  "name": "My CLI Agent",
  "version": "1.0.0",
  "description": "A CLI-based agent that requires login",
  "license": "MIT",
  "auth": {
    "method": "TERMINAL_AUTH",
    "setupCommand": "my-agent auth login --interactive",
    "description": "Run once to authenticate with the MyApp API"
  },
  "distribution": {
    "npx": { "packageName": "my-cli-agent@1.0.0" }
  }
}
```

## Error Messages

When an unauthenticated agent is blocked from provider registration, the structured error includes:

```
Agent 'my-cli-agent' requires authentication (TERMINAL_AUTH).
Run once to authenticate with the MyApp API
Run setup command: my-agent auth login --interactive
```