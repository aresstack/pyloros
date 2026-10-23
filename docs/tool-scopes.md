# Per-tool scopes

Pyloros authenticates a request against one gateway scope (`security.required-scope`,
default `mcp`). On top of that, a provider can declare a scope for an individual tool
via `ToolProvider.requiredScope(upstreamToolName)`. `ToolRouter` then refuses a call
whose token does not carry that scope.

## Enforcement is opt-in

`security.tool-scopes` / `TOOL_SCOPES` (default `false`).

The deployed ChatGPT connector requests exactly one scope. This is not an assumption:
the reverse-proxy log of the production webserver contains the real authorization
requests, for example

```
GET /oauth/authorize?response_type=code&client_id=angel
    &redirect_uri=https%3A%2F%2Fchatgpt.com%2Fconnector%2Foauth%2F…
    &scope=mcp&code_challenge=…&code_challenge_method=S256
    &resource=https%3A%2F%2Faskai.aresstack.de%2F&state=…
```

with the token endpoint being called by `openai-connectors-oauth/1.0`. A token issued
from such a request carries `scope=mcp` and nothing else — the stored refresh tokens
confirm it (`client=chatgpt scope=mcp resource=https://pyloros.aresstack.de/`).

Enforcing per-tool scopes by default would therefore deny every writing tool to the
only client that currently exists. Switch `TOOL_SCOPES=true` on once a client actually
requests the finer scopes and the webserver has them approved for the resource.

## Declared mappings

| Tool | Scope | Reason |
| --- | --- | --- |
| `pyloros__ping` | — | returns a constant string |
| `pyloros__skills_save` | `pyloros:write` | writes into the Pyloros user directory |
| `pyloros__skills_delete` | `pyloros:write` | deletes from the Pyloros user directory |
| `acp_registry__install_agent` | `pyloros:admin` | installs an executable agent on the host |
| `acp_registry__update_agent` | `pyloros:admin` | replaces an installed executable |
| `acp_registry__uninstall_agent` | `pyloros:admin` | removes an installed executable |
| `acp_registry__configure_agent` | `pyloros:admin` | changes prefix, view, enabled state |
| `acp_registry__list_agents` | — | reads the registry |
| `acp_registry__list_installed_agents` | — | reads the installed-agent store |
| `acp_registry__get_agent_status` | — | reads one agent's status |
| `acp_registry__refresh_registry` | — | reloads the cache from the configured URL |

## Deliberately not mapped

These are gaps, not oversights. A wrong mapping either blocks a working tool or
pretends a protection that does not exist, so nothing here is guessed:

- **Every tool proxied from an upstream MCP server** — `GenericMcpToolProvider`,
  `IdeaToolProvider`, `IntellijIndexToolProvider`, `GitHubToolProvider`. Their tool
  names and semantics come from the upstream server at runtime; Pyloros cannot tell a
  reading tool from a writing one. A per-upstream or per-tool-name mapping would have
  to be configured by the operator, or declared by the upstream through MCP metadata
  that none of them currently sends.
- **`AcpVirtualToolProvider`** — forwards into an installed ACP agent, whose tool set
  is defined by that agent.
- **`TargetPlatformSkillsToolProvider`** and **`IntellijMcpSetupToolProvider`** — not
  yet reviewed tool by tool.
- **`ExampleEchoToolProvider`** — sample plugin; echoes its input and protects nothing.

## Scope names

`pyloros:write` and `pyloros:admin` are the names the gateway demands. They have no
effect until the operator adds them to the resource's scope catalogue in the webserver
and a client requests them: the OAuth server only ever issues scopes that were both
requested and approved.
