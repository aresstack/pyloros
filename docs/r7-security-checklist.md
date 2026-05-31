# R7 Security Checklist

## Core Security Rules

| # | Rule | Status | Verified By |
|---|---|---|---|
| 1 | No automatic agent installation | ✅ | `AgentLifecycleService.install()` requires explicit call |
| 2 | No automatic execution after registry refresh | ✅ | Registry refresh is read-only; no install/execute side effects |
| 3 | Installed external agents only with prefix (not root) | ✅ | Default prefix is `agentId/`; root `/` requires explicit opt-in |
| 4 | Root tools only explicit opt-in | ✅ | `InstalledAgentAcpProviderFactory.validatePrefix()` allows `/` but logs a note |
| 5 | `agentToolView` is non-public | ✅ | Default `agentId-agent-view`; `"public"` is rejected by `AgentToolViewValidator` |
| 6 | R6 Trusted-View-Token used for non-public MCP view selection | ✅ | `AcpVirtualToolProvider.injectedMcpServers()` injects `X-Pyloros-Injected-View-Token` |
| 7 | Secrets not logged | ✅ | All lifecycle ops use `sanitize()`; `AgentAuthFlowTest` validates no credential leakage |
| 8 | Auth Required not silently swallowed | ✅ | `InstalledAgentAcpProviderFactory` rejects `authStatus==REQUIRED` with structured error |
| 9 | Corrupted registry/installed-agent files don't cause unsafe config | ✅ | `AcpRegistryCache` fallbacks gracefully; `InstalledAgentStore` skips corrupted entries |
| 10 | Recursion protection remains active | ✅ | `AgentToolViewValidator` checks cross-provider collisions; `ExistingAcpProviderIds` passed from `PylorosApplication` |

## ACP Agent Isolation

| # | Rule | Status |
|---|---|---|
| A1 | Each installed agent gets unique `agentToolView` | ✅ `agentId + "-agent-view"` |
| A2 | Agents cannot see `"public"` view | ✅ `AgentToolViewValidator` rejects `"public"` |
| A3 | Agents cannot see other agents' views by default | ✅ Per-agent unique views |
| A4 | `session/new` injects `mcpServers.pyloros` with trusted headers | ✅ `AcpVirtualToolProvider.injectedMcpServers()` |
| A5 | Auth-required agents are blocked at provider registration | ✅ `InstalledAgentAcpProviderFactory` auth check |

## Data Integrity

| # | Rule | Status |
|---|---|---|
| D1 | Registry cache is validated before use | ✅ `AcpRegistryClient.validate()` |
| D2 | Installed agent store uses atomic writes | ✅ `InstalledAgentStore.writeDocument()` atomic move |
| D3 | Corrupted agent entries are skipped, others preserved | ✅ `InstalledAgentStore.listAll()` catches per-entry exceptions |
| D4 | Store JSON is backward-compatible | ✅ `@JsonIgnoreProperties(ignoreUnknown = true)` everywhere |
| D5 | Distribution archive extraction validates entries before extracting | ✅ `DefaultAgentInstaller` preflight validation |

## Auth Security

| # | Rule | Status |
|---|---|---|
| AU1 | No credentials stored in Pyloros | ✅ Only metadata: `AgentAuthMethod`, `AgentAuthStatus`, setup command, description |
| AU2 | `TERMINAL_AUTH` requires `setupCommand` in registry | ✅ Validated by `AcpRegistryClient.validate()` |
| AU3 | `auth_status=REQUIRED` cannot be set via `configure_agent` | ✅ Blocked in `AcpRegistryManagementToolProvider.handleConfigureAgent()` |
| AU4 | No automatic auth execution | ✅ Setup commands are user-facing only |
| AU5 | Auth metadata is not logged as secrets | ✅ `sanitize()` excludes only non-alphanumeric chars |