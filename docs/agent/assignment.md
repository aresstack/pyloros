# Current Assignment

Task: 005 - Repository hygiene and release baseline

Read AGENTS.md first. Use this file as the single source of truth.

Goal: Prepare repository for first stable release milestone.

Scope:
1. Review and extend .gitignore for data/, logs/ and local IDE artifacts
2. Update README.md: clarify Apache → HTTP Backend, OAuth Refresh flow, IntelliJ Tool Prefix (intellij/...)
3. Check status of open requirements with completed commits
4. Execute final build
5. Prepare for push (but DO NOT push)

Allowed:
- Extend .gitignore
- Update README.md
- Review build status
- Run full Gradle build
- Verify no uncommitted runtime data is about to be pushed

Not allowed:
- no git push
- no broad code refactoring
- no new features or requirements

Status to verify (all completed):
- 001-A bis 001-E: implemented
- 003-A/B: completed, Commit c7e068c
- 002-A: completed, Commit 7466f86
- 002-B: completed, Commit 1ad0303
- 002-C: completed, Commit 921c002
- 002-D: completed, Commit f7ececf

Required checks:
1. Verify .gitignore properly excludes:
   - data/
   - logs/
   - Local IDE artifacts (.idea, *.iml, etc.)
2. Update README.md with current implementation details
3. Build is green with JDK 21
4. No uncommitted runtime files ready to be staged
5. Verify all previous commits are in local history

Final report:
Overwrite docs/agent/report.md completely.
Include files changed, .gitignore updates, README changes, build results, and confirmation ready for push.
