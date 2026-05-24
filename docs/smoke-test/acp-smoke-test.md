# ACP Virtual Provider – Smoke Test

## Prerequisites

- Java 21 installed
- Pyloros built: `./gradlew build`
- ACP provider configured in `mcp.json` or equivalent configuration

## Configuration

Add an ACP provider to the Pyloros configuration:

```json
{
  "views": {
    "public": {
      "includeProviders": ["pyloros", "intellij", "github", "copilot"]
    },
    "agent": {
      "includeProviders": ["pyloros", "intellij", "github"],
      "excludeProviders": ["copilot", "goose", "claude", "pyloros-ai"]
    }
  },
  "providers": [
    {
      "id": "copilot",
      "type": "acp",
      "prefix": "copilot/",
      "exposeInViews": ["public"],
      "agentToolView": "agent",
      "process": {
        "command": "copilot",
        "args": ["--acp", "--stdio"],
        "workingDirectory": "C:/Projects/pyloros"
      },
      "execution": {
        "defaultTaskTimeoutSeconds": 900,
        "maxEventsPerTask": 1000,
        "maxEventTextChars": 12000,
        "maxResultTextChars": 24000
      }
    }
  ]
}
```

## Test Steps

### 1. Start Pyloros

```bash
./gradlew :pyloros-app:run
```

Verify in logs:
```
Pyloros listening on port 8080 ...
```

### 2. Verify tools/list contains ACP tools

```bash
curl -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "method": "tools/list", "id": 1}'
```

Expected: response contains tools `copilot/start_task`, `copilot/run_task`, `copilot/get_task_status`, `copilot/get_task_result`, `copilot/cancel_task`.

### 3. Start an async task

```bash
curl -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "copilot/start_task",
      "arguments": {"prompt": "List files in the current directory"}
    },
    "id": 2
  }'
```

Expected: response contains `taskId` and `state: "RUNNING"`.

### 4. Get task status

Use the `taskId` from step 3:

```bash
curl -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "copilot/get_task_status",
      "arguments": {"taskId": "<TASK_ID>", "includeEvents": true}
    },
    "id": 3
  }'
```

Expected: response contains current state and events.

### 5. Get task result

```bash
curl -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "copilot/get_task_result",
      "arguments": {"taskId": "<TASK_ID>"}
    },
    "id": 4
  }'
```

Expected: result for completed task, or error message if still running.

### 6. Run a synchronous task

```bash
curl -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "copilot/run_task",
      "arguments": {"prompt": "What is 2+2?"}
    },
    "id": 5
  }'
```

Expected: response contains completed result.

### 7. Cancel a running task

Start a task, then immediately cancel:

```bash
curl -X POST http://localhost:8080/pyloros \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "copilot/cancel_task",
      "arguments": {"taskId": "<TASK_ID>"}
    },
    "id": 6
  }'
```

Expected: response contains `state: "CANCELLED"`.

## Common Problems

| Problem | Cause | Solution |
|---------|-------|----------|
| Command not found | `process.command` is not in PATH | Install the agent or use absolute path |
| Working directory invalid | Path does not exist | Create directory or fix config |
| Agent speaks no ACP | Agent started but doesn't respond to JSON-RPC | Verify agent supports ACP stdio mode |
| Agent Tool View misconfigured | `agentToolView` references non-existent view | Define the view in configuration |
| Timeout | Agent takes too long | Increase `defaultTaskTimeoutSeconds` |
| Recursion prevented | ACP provider references itself in agent view | Remove ACP provider from agent view |
| Provider unavailable | Process can't start | Check logs, verify command and working directory |

## Testing with Fake Agent

For development without a real ACP agent, the `AcpVirtualToolProvider` returns placeholder results. Integration tests use this mode to validate the full MCP flow without external dependencies.
