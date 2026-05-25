# Release 6 – Java 21 ACP Manager Agent

## 1. Summary

Release 6 replaces the abandoned Release 5 LangChain-in-core approach with an
**ACP Manager Agent** architecture. Agent logic lives outside Pyloros core as a
separate ACP-based process that receives the Pyloros MCP aggregator as its
injected tool server.

Pyloros remains a **Capability Gateway and Tool Aggregator**. The manager agent
is a consumer of the aggregated MCP tool surface, not an extension of the core.

---

## 2. Architecture

### 2.1 Pyloros Core (unchanged)

```text
Pyloros Core
  -> ProviderRegistry
  -> ToolCatalog
  -> ToolRouter
  -> Native / MCP / ACP / Plugin Provider
```

### 2.2 Manager Agent (new in R6)

```text
ACP Client / IDE / Virtual ACP Provider
  -> session/new
  -> inject Pyloros MCP server as tool server
  -> Java 21 ACP Manager Agent
  -> uses aggregated MCP tools (tools/list, tools/call)
  -> plans, executes, reviews, reports
```

### 2.3 Protocol Roles

| Protocol | Role |
|----------|------|
| **MCP**  | Tool and resource layer — discovery (`tools/list`) and invocation (`tools/call`) |
| **ACP**  | Agent session and IDE/client integration layer (`session/new`, `session/prompt`, `session/update`) |
| **A2A**  | (future) Agent-to-agent delegation for long-running or parallel sub-tasks |

### 2.4 Agent Tool View

The manager agent does **not** see the public MCP view. It receives a dedicated
`agent` view that excludes other ACP providers (recursion prevention):

```text
agent view:
  get_status
  intellij/*
  github/*
  filesystem/*
  app/*
```

Excluded from agent view:

```text
copilot/*      (would cause recursion)
goose/*        (other ACP provider)
claude/*       (other ACP provider)
```

---

## 3. Recursion Protection

The `AgentToolViewValidator` enforces these rules at provider registration time:

1. `agentToolView` must not be `"public"` — agents must never see the public view.
2. `agentToolView` must not reference the provider's own ID.
3. `agentToolView` must not reference any other ACP provider ID.
4. `agentToolView` must not be a view where the ACP provider's own tools are exposed.

These rules prevent:
- An agent calling its own `run_task` → infinite recursion
- Agent A delegating to Agent B which delegates back to Agent A
- An agent invoking itself via the public view

The validator is tested in `AgentToolViewValidatorTest`.

---

## 4. Session Lifecycle

```text
                       ┌─────────────────┐
                       │  AgentTask       │
                       ├─────────────────┤
                       │ taskId           │
                       │ providerId       │
                       │ acpSessionId     │
                       │ state            │
                       │ cwd              │
                       │ prompt           │
                       │ startedAt        │
                       │ updatedAt        │
                       │ completedAt      │
                       │ events           │
                       │ pendingPerms     │
                       │ result / error   │
                       └─────────────────┘

States: CREATED → RUNNING → COMPLETED
                         → FAILED
                         → CANCELLED
                         → TIMEOUT
                         → WAITING_FOR_PERMISSION
```

### 4.1 Synchronous Flow (`run_task`)

1. Pyloros launches the ACP agent process (stdio JSON-RPC).
2. Sends `session/new` with `cwd`.
3. Sends `session/prompt` with the user prompt.
4. Polls `session/update` events until a terminal event arrives.
5. Collects events, result, and returns the structured response.

### 4.2 Asynchronous Flow (`start_task` / `get_task_status` / `get_task_result`)

Same as synchronous, but task execution runs in a background thread.
The client polls status and retrieves the result when complete.

---

## 5. MCP Injection

The agent receives the Pyloros MCP aggregator as its tool server. Concretely:

- The ACP process is launched with environment variables or command-line args
  pointing to the Pyloros MCP endpoint.
- The agent uses standard MCP client calls (`tools/list`, `tools/call`) to
  discover and invoke the tools visible in its `agentToolView`.
- The agent does **not** duplicate tool registry, MCP upstream management, or
  provider lifecycle — all of that is handled by Pyloros core.

Configuration example (`mcp.json`):

```json
{
  "acpProviders": [
    {
      "id": "manager",
      "type": "acp",
      "prefix": "manager/",
      "exposeInViews": ["public"],
      "agentToolView": "agent",
      "process": {
        "command": "java",
        "args": ["-jar", "pyloros-manager-agent.jar", "--mcp-url", "http://localhost:8080/pyloros"],
        "workingDirectory": "/workspace"
      },
      "execution": {
        "defaultTaskTimeoutSeconds": 900,
        "maxEventsPerTask": 1000
      }
    }
  ]
}
```

---

## 6. What Release 6 Removes from Core

| Removed | Rationale |
|---------|-----------|
| `ProviderType.LANGCHAIN` | No LangChain provider in core |
| `LangChainVirtualToolProvider` concept | Never implemented; replaced by ACP agent |
| `pyloros-ai/ask` as core tool | Agent logic is not a core tool |
| Core dependency on LangChain4j/Ollama | Agent chooses its own LLM stack |

---

## 7. What Release 6 Provides

| Component | Status |
|-----------|--------|
| `AcpVirtualToolProvider` | ✅ Implemented |
| `AgentToolViewValidator` (recursion protection) | ✅ Implemented and tested |
| `AcpAgentClient` (stdio JSON-RPC) | ✅ Implemented |
| `AcpProcessLauncher` | ✅ Implemented |
| `AgentTask` lifecycle management | ✅ Implemented |
| `AcpProviderFactory` (mcp.json config) | ✅ Implemented |
| `AcpToolDefinitions` (run_task, start_task, etc.) | ✅ Implemented |
| Smoke test documentation | ✅ See `docs/smoke-test/` |
| Architecture documentation | ✅ This document |

---

## 8. Non-Goals for Release 6

- Full workflow/work-graph engine
- Long-term memory / RAG
- Cloud LLM requirement
- Chat history UI
- A2A inter-agent protocol
- Specific SDK choice mandate (agent decides its own stack)

---

## 9. Future Direction

The manager agent can later evolve into:

- Deterministic workflow/work-graph core
- Task decomposition and delegation to worker agents
- Review and governance loops
- Persistent checkpoints
- Reconciliation/polling for missing events
- Audit and observability
- Optional A2A for real agent-to-agent communication

---

## 10. Definition of Done

Release 6 is complete when:

- [x] Old Release 5 LangChain core approach is fully replaced by ACP Manager Agent architecture
- [x] `ProviderType.LANGCHAIN` removed from core
- [x] Minimal Java 21 ACP Manager Agent infrastructure is present (`AcpVirtualToolProvider`)
- [x] Agent can use the Pyloros MCP aggregator as injected tool server
- [x] Agent tool view is clearly defined and enforced
- [x] Recursion protection is documented and tested (`AgentToolViewValidator`)
- [x] Smoke test documents the flow: Agent → MCP tools/list → MCP tools/call → Agent response
- [x] Architecture is documented (this document)
