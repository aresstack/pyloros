# Release 6 – Manager Agent Smoke Test

This smoke test validates the Release 6 flow:
**Agent → MCP tools/list → MCP tools/call → Agent response**

Parent issue: `aresstack/pyloros#59`

## Scope and current implementation depth

- Pyloros already provides the ACP runtime infrastructure (`AcpVirtualToolProvider`,
  ACP session handling, recursion validation).
- A dedicated standalone manager-agent binary is still a separate deliverable.
- Therefore this document contains:
  1. manual smoke steps to run with an ACP-compatible agent binary when available
  2. automation-ready steps that can be scripted in CI later

## Prerequisites

- Java 21 installed
- Pyloros built: `./gradlew build`
- An ACP-compatible agent binary available (for example a future `pyloros-manager-agent.jar`)

## Configuration

Configure a manager agent in `mcp.json`:

```json
{
  "servers": {
    "intellij": { "url": "http://localhost:63342/pyloros", "type": "sse" }
  },
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
        "defaultTaskTimeoutSeconds": 120,
        "maxEventsPerTask": 500,
        "maxEventTextChars": 12000,
        "maxResultTextChars": 24000
      }
    }
  ]
}
```

## Flow Under Test

```text
Client
  ↓ MCP tools/call manager/run_task {prompt: "..."}
Pyloros (AcpVirtualToolProvider)
  ↓ launches ACP agent process (stdio JSON-RPC)
  ↓ session/new
  ↓ session/prompt
ACP Manager Agent
  ↓ MCP tools/list (on agent view via injected Pyloros MCP endpoint)
  ↓ MCP tools/call (invokes discovered tools)
  ↓ session/update events (text, completion)
Pyloros
  ↓ aggregates events
  ↓ returns structured result
Client
```

## Manual smoke-test steps (current)

### 1. Start Pyloros

```bash
./gradlew :pyloros-app:run
```

Verify in logs:
```
Pyloros listening on port 8080 ...
[ACP-PROVIDER] registered provider=manager prefix=manager/ agentToolView=agent
```

### 2. Verify tools/list exposes manager tools

```bash
curl -s -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}' | jq '.result.tools[] | select(.name | startswith("manager/"))'
```

Expected tools:
- `manager/run_task`
- `manager/start_task`
- `manager/get_task_status`
- `manager/get_task_result`
- `manager/cancel_task`

### 3. Run a task (synchronous)

```bash
curl -s -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "manager/run_task",
      "arguments": {"prompt": "List available tools and summarize them"}
    },
    "id": 2
  }'
```

Expected: The manager agent:
1. Calls `tools/list` on the injected Pyloros MCP endpoint (sees `agent` view)
2. Discovers tools like `get_status`, `intellij/*`, `github/*`
3. Returns a structured summary

Response structure:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [{"type": "text", "text": "{\"taskId\":\"...\",\"state\":\"COMPLETED\",\"result\":\"...\"}"}],
    "isError": false
  }
}
```

### 4. Start a task (asynchronous) and poll

```bash
# Start
RESPONSE=$(curl -s -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "manager/start_task",
      "arguments": {"prompt": "Get the status of the Pyloros server"}
    },
    "id": 3
  }')

TASK_ID=$(echo "$RESPONSE" | jq -r '.result.content[0].text' | jq -r '.taskId')
echo "Task ID: $TASK_ID"

# Poll status
curl -s -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"manager/get_task_status\",
      \"arguments\": {\"taskId\": \"$TASK_ID\", \"includeEvents\": true}
    },
    \"id\": 4
  }"

# Get result (after task completes)
curl -s -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"manager/get_task_result\",
      \"arguments\": {\"taskId\": \"$TASK_ID\"}
    },
    \"id\": 5
  }"
```

### 5. Verify recursion protection

Attempt to configure an agent that references itself:

```json
{
  "acpProviders": [
    {
      "id": "manager",
      "type": "acp",
      "prefix": "manager/",
      "exposeInViews": ["public"],
      "agentToolView": "manager",
      "process": { "command": "java", "args": ["-jar", "agent.jar"] }
    }
  ]
}
```

Expected: Pyloros rejects the configuration at startup:
```
[ACP-PROVIDER] failed to create provider=manager reason=agentToolView must not reference ACP provider itself: manager
```

### 6. Verify agent cannot see public view

Configure `agentToolView: "public"`:

Expected rejection:
```
agentToolView must not be 'public' — ACP agents must not see the public tool view: public
```

## Existing automated baseline in this repository

The following unit and integration tests validate the R6 flow without a real agent process:

| Test class | Coverage |
|------------|----------|
| `AgentToolViewValidatorTest` | Recursion protection rules |
| `AcpIntegrationTest` | Full session lifecycle with `FakeAcpAgent` |
| `AcpVirtualToolProviderTest` | Provider behavior, timeout, cancellation |
| `AcpRuntimeIntegrationTest` | End-to-end with real process launch |

Run all ACP tests:

```bash
./gradlew :pyloros-server:test --tests "com.aresstack.pyloros.acp.*"
```

## Later automatable smoke-test flow

The manual steps above are intentionally written so they can later be automated
as a scripted smoke test:

1. Start Pyloros and assert ACP provider registration log line.
2. Call MCP `tools/list` and assert `manager/*` tool names are present.
3. Call MCP `tools/call` with `manager/run_task` and assert successful response.
4. Call MCP `tools/call` with `manager/start_task`, poll `get_task_status`, then `get_task_result`.
5. Run negative config checks for recursion protection (`agentToolView` self/public).

## Success Criteria

- [ ] Manager agent tools appear in `tools/list`
- [ ] `run_task` launches agent, sends prompt, returns result
- [ ] Agent uses MCP `tools/list` and `tools/call` on the injected endpoint
- [ ] Agent sees only tools in its `agentToolView` (not other ACP providers)
- [ ] Recursion is prevented at configuration time
- [ ] Task timeout and cancellation work correctly
