# Report: Release 6 – Java 21 ACP Manager Agent

## What was verified, changed or implemented?

1. **Removed `ProviderType.LANGCHAIN`** from the core enum — the issue explicitly states
   "kein eigener ProviderType.LANGCHAIN im Core". The value was unused in all Java code.

2. **Marked Release 5 LangChain requirements doc as superseded** — added a prominent
   notice at the top of `docs/requirements/pyloros-langchain-extension.md` pointing to R6.

3. **Created Release 6 architecture documentation** — comprehensive doc covering:
   - Architecture overview (core stays unchanged, agent is external)
   - Protocol roles (MCP for tools, ACP for sessions)
   - Agent tool view definition
   - Recursion protection rules
   - Session lifecycle
   - MCP injection mechanism
   - What R6 removes from core vs. what it provides
   - Definition of Done checklist

4. **Created R6 Manager Agent smoke test** — step-by-step test document showing the
   Agent → MCP tools/list → MCP tools/call → Agent response flow, including recursion
   protection verification.

## Which files were changed or newly created?

| File | Change |
|------|--------|
| `pyloros-server/src/main/java/com/aresstack/pyloros/provider/ProviderType.java` | Removed `LANGCHAIN` enum value |
| `docs/requirements/pyloros-langchain-extension.md` | Added superseded notice |
| `docs/requirements/006-acp-manager-agent.md` | **New** — R6 architecture doc |
| `docs/smoke-test/r6-manager-agent-smoke-test.md` | **New** — R6 smoke test |

## Which architecture decision was touched?

- ADR: Replace Release 5 LangChain-in-core approach with ACP Manager Agent architecture
- Agent logic lives outside Pyloros core as an ACP-based sidecar process
- Pyloros remains Capability Gateway and Tool Aggregator

## Which tests, builds and runtime checks were executed?

- `./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava` → SUCCESS
- `./gradlew --no-daemon :pyloros-server:test` → All tests pass
- `./gradlew --no-daemon build` → Full build successful (57 tasks)
- CodeQL Security Scan → Passed (trivial change)

## Result

Successful

## Exact commit hash

806293f (branch: copilot/release-6-java-21-acp-manager-agent)

