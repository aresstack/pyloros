# Current Assignment

Task: Repo scan for IDEA MCP upstream integration.

Use LocalCodeSearch before answering.

Do not change code. Do not commit. Do not push.

Search terms: IdeaSseSession, IdeaJsonRpcClient, IdeaMcpClient, IdeaToolProvider, idea.mcp, idea__, tools/list, tools/call, initialize, endpoint.

Write the final result to docs/agent/report.md and replace the old content completely.

The report must answer:
- Which files contain the IDEA MCP upstream integration?
- Where is the SSE endpoint discovered and stored?
- Where are JSON-RPC requests sent to IDEA?
- Where are initialize and tools/list implemented?
- Where are IDEA tools prefixed with idea__?
- Is there any direct IDEA RPC call outside IdeaMcpClient or IdeaJsonRpcClient?
- Is tools/call forwarding already implemented anywhere?
- Which fragile spots did you find?
- What is the recommended next step?

No commit is expected for this task.
