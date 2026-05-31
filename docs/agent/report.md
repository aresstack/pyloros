# R7-06, R7-07, R7-08 — Report

## What was verified, changed or implemented?

### R7-06: ACP registry agent auth flow support
- New types: `AgentAuthMethod` (NONE, AGENT_AUTH, TERMINAL_AUTH), `AgentAuthStatus` (NOT_REQUIRED, REQUIRED, CONFIGURED), `AuthRequirements` record
- `RegistryAgent`: optional `auth` field (backward-compat)
- `InstalledAgent`: `authMethod`, `authStatus`, `authSetupCommand`, `authDescription` fields + `withAuthStatus()` convenience method
- `InstalledAgentStoreDocument.InstalledAgentEntry`: mirrored auth fields
- `InstalledAgentStore`: serialize/deserialize auth fields with defaults for unknown values
- `AgentLifecycleService`: extract auth during install/update
- `InstalledAgentAcpProviderFactory`: skip agents with `authStatus == REQUIRED`, emit structured failure
- `AcpRegistryClient.validate()`: validate TERMINAL_AUTH requires setupCommand
- 13 tests in `AgentAuthFlowTest`

### R7-07: Headless management API
- `AcpRegistryManagementToolProvider`: 8 native tools (list_agents, refresh_registry, install_agent, update_agent, uninstall_agent, configure_agent, list_installed_agents, get_agent_status)
- Structured JSON output, deterministic error handling
- Auth status visible, configure blocks setting REQUIRED
- 20 tests in `AcpRegistryManagementToolTest`

### R7-08: Security hardening, smoke test, release audit
- `docs/r7-security-checklist.md`: 20 verified security rules
- `release-notes/R7.md`: feature summary for all R7 sub-issues
- `docs/agent-auth.md`: auth flow documentation and security model
- `R7EndToEndSmokeTest`: 7 E2E tests covering full pipeline

## Which files were changed or newly created?

### New files (10):
- `pyloros-server/.../acp/registry/AgentAuthMethod.java`
- `pyloros-server/.../acp/registry/AgentAuthStatus.java`
- `pyloros-server/.../acp/registry/AuthRequirements.java`
- `pyloros-server/.../acp/registry/AcpRegistryManagementToolProvider.java`
- `pyloros-server/.../test/.../acp/registry/AgentAuthFlowTest.java`
- `pyloros-server/.../test/.../acp/registry/AcpRegistryManagementToolTest.java`
- `pyloros-server/.../test/.../acp/registry/R7EndToEndSmokeTest.java`
- `docs/agent-auth.md`
- `docs/r7-security-checklist.md`
- `release-notes/R7.md`

### Changed files (11):
- `RegistryAgent.java` — added `AuthRequirements auth` field
- `InstalledAgent.java` — added 4 auth fields + `withAuthStatus()`
- `InstalledAgentStoreDocument.java` — mirrored auth fields in `InstalledAgentEntry`
- `InstalledAgentStore.java` — auth serialization/deserialization
- `AgentLifecycleService.java` — auth extraction in install/update
- `InstalledAgentAcpProviderFactory.java` — auth check before registration
- `AcpRegistryClient.java` — auth validation
- `InstalledAgentStoreTest.java` — updated `createAgent()` for new fields
- `InstalledAgentAcpProviderFactoryTest.java` — updated `createAgent()` + inline constructors
- `AgentLifecycleServiceTest.java` — added `null` auth param in helpers
- `AcpRegistryClientTest.java` — added `null` auth param in inline constructor

## Which architecture decision was touched?

- Auth is metadata-only in Pyloros (no credential storage) — consistent with "secrets stay with the agent" principle
- Management tools follow existing native ToolProvider pattern
- Auth block at provider generation follows existing failure pattern in `InstalledAgentAcpProviderFactory`
- All new types are in `acp/registry/` package, consistent with existing R7 code

## Which tests, builds and runtime checks were executed?

- `./gradlew :pyloros-server:test --tests com.aresstack.pyloros.acp.registry.*`: 50+ tests passed
- Full IDE build (`Build → Rebuild Project`): passed
- No regressions in existing tests

## Result: successful

## Exact commit hash

PR #94: `https://github.com/aresstack/pyloros/pull/94`
Branch: `copilot/r7-06-acp-auth-flow-support` → `copilot/release-7-public-acp-registry-installer`
Commits:
- `8cc1300` feat(R7-06): Add ACP registry agent auth flow support
- `6bf737c` feat(R7-07): Add headless management API tools for ACP registry agents
- `1c501f5` docs(R7-08): Add security checklist, release notes and E2E smoke test