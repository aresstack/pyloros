# 003 - Upstream Restart Resilience

## Problem

During development the IDE MCP upstream may be restarted while Pyloros is running. Pyloros must not stop or become unusable when that happens.

## Goal

Pyloros should handle temporary upstream unavailability gracefully and continue serving all other providers.

## Requirements

- Pyloros must keep running if the IDE MCP upstream disappears.
- The current upstream endpoint must be treated as stale after a disconnect.
- Calls that depend on the unavailable upstream must return a controlled MCP error result.
- Other providers, especially pyloros__ping, must remain available.
- Reconnect attempts should continue in the background.
- Logs should clearly show connection loss, reconnect attempts, and successful reconnect.

## Suggested configuration

- idea.mcp.reconnect.delay.ms
- idea.mcp.call.timeout.ms
- idea.mcp.clear-endpoint-on-disconnect

## Acceptance criteria

- Restarting the IDE MCP upstream does not stop Pyloros.
- A call during upstream downtime returns isError=true instead of failing the process.
- After the upstream is available again, Pyloros reconnects.
- After reconnect, the tool list can be refreshed.
- Build is green.

## Not in scope

- Persistent session storage.
- Full request queueing across restarts.
- OAuth refresh token handling. That is tracked in requirement 002.
