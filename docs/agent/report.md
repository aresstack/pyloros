What was verified, changed or implemented?
- Full ACP Virtual Provider implementation for Release 3
- Domain model: AgentTask, AgentTaskId, AgentTaskState, PendingPermission
- Configuration: AcpProviderConfiguration, AcpProcessConfiguration, AcpExecutionConfiguration
- Provider: AcpVirtualToolProvider implementing ToolProvider with 5 MCP tools
- Tool definitions: AcpToolDefinitions with JSON Schema input schemas
- Repository: AgentTaskRepository interface + InMemoryAgentTaskRepository
- Process management: AcpProcessLauncher, AcpProcessHandle, AcpProcessFailure
- JSON-RPC client: AcpJsonRpcConnection, AcpAgentClient, AcpEventMapper
- Audit: AcpAuditLogger with structured logging
- Smoke test documentation

Which files were changed or newly created?
- 19 new production files in pyloros-server/src/main/java/com/aresstack/pyloros/acp/
- 3 new test files in pyloros-server/src/test/java/com/aresstack/pyloros/acp/
- docs/smoke-test/acp-smoke-test.md

Which architecture decision was touched?
- ProviderType.ACP reused (already existed)
- ACP provider integrates via existing ToolProvider/ToolCatalog/ToolRouter
- No special MCP routes; ACP tools are regular MCP tools
- Agent Tool View prevents recursion
- In-memory task repository (no persistence across restarts)

Which tests, builds and runtime checks were executed?
- ./gradlew build — BUILD SUCCESSFUL
- ./gradlew :pyloros-server:test — All tests pass
- ./gradlew :pyloros-server:compileJava — Clean compilation

Result: successful

If failed: exact error and recommended next step
- Not failed.

Exact commit hash, or No commit created
- See PR commits on branch copilot/release-3-acp-virtual-provider
