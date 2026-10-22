# Active Task — Harden MCP HTTP and SSE stream handling

## Approval and scope

Approved by the user on 2026-10-22 in the ChatGPT conversation about the intermittent MCP client error Error in input stream. This assignment replaces the previous active assignment for the current task; the earlier Maven Central publishing work remains documented in the Git history and the main-branch report.

- Repository: aresstack/pyloros
- Base commit: 217fe5c96a4eb90ef64a84a2f9294e5ad847d5cb
- Working branch: fix/mcp-stream-robustness-20261022
- Pull request: https://github.com/aresstack/pyloros/pull/108
- The user confirms that the deployed server runs the current main version at the stated baseline, including the root-path alignment made to match Ask AI.

Do not modify the separate aresstack/webserver repository or its TLS configuration unless later evidence points to that layer. Do not merge the PR, deploy the branch, or create/modify a release without separate user approval.

## Problem

The ChatGPT MCP integration intermittently reports Error in input stream after periods of normal operation. The precise production cause has not yet been correlated with a failure timestamp and server/proxy logs.

## Work completed

1. Make JSON-RPC response serialization safe when IDs, results, or exception messages are null. Ensure asynchronous failure paths produce a completed HTTP response.
2. Align the public endpoint with Streamable HTTP semantics: return 405 Method Not Allowed with Allow: POST for GET when no server-to-client SSE stream is implemented, and return bodyless 202 Accepted for notifications.
3. Guard the IDEA SSE lifecycle against duplicate reconnect attempts and callbacks from superseded connection attempts. Cancel pending reconnect timers on stop.
4. Decode UTF-8 and normalize SSE line endings correctly across arbitrary network-buffer boundaries.
5. Add regression and transport-conformance tests for these behaviours.

## Acceptance and verification

- Run the full Gradle build and all targeted tests.
- Verify that regression tests fail against the previous lifecycle behaviour where they are intended to detect a regression.
- Keep the work isolated to Pyloros; do not change the webserver/TLS repository.
- Record changed files, test results, commits, and remaining risks in docs/agent/report.md.
- Keep PR #108 as Draft pending review. No merge, deployment, or release is authorized by this assignment.

## Known limitations

- The exact production trigger for Error in input stream remains unproven until a real failure timestamp can be correlated with Pyloros and Caddy logs.
- Generic Streamable HTTP upstream lifecycle issues in StreamableHttpMcpUpstreamClient are outside the current change set and should be considered separately if generic HTTP upstreams are configured.
