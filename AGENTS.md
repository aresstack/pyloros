# AGENTS.md

## Mandatory workflow for junior implementers

Before starting any work, always read:

`docs/agent/assignment.md`

That file is the single source of truth for the current task. Do not start from chat history, assumptions, or older requirements unless the current assignment explicitly references them.

After finishing the task, do not report results in chat. Instead, overwrite:

`docs/agent/report.md`

The assignment file and report file are current-state files. Their old content must be replaced completely when a new task or report is written.

## Report format

Every report must contain:

- What was verified, changed or implemented?
- Which files were changed or newly created?
- Which architecture decision was touched?
- Which tests, builds and runtime checks were executed?
- Result: successful or failed
- If failed: exact error and recommended next step
- Exact commit hash, or No commit created

## Project rules

Use Java 21, Vert.x and Gradle Groovy DSL. Do not use Spring.

PylorosApplication stays bootstrap and wiring only.
OAuth logic belongs in OAuth services.
Tool aggregation goes through ToolProvider and ToolRegistry.
New tool sources are separate providers.
Optional upstreams must not prevent startup.

Do not push unless explicitly requested.
