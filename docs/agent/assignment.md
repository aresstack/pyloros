# Current Assignment

Task: 008-B - Maven Central publishing readiness metadata

Read AGENTS.md first. Use this file as the single source of truth.

## Goal

Prepare library modules for future Maven Central publishing metadata quality,
without executing any remote publication.

## Scope

- No external publication.
- No Sonatype/Portal token setup.
- No secrets.
- Prepare publishing metadata for library modules only:
  - `pyloros-server`
  - `pyloros-upstream-idea`
- Metadata fields to define:
  - group
  - version
  - description
  - license
  - developers
  - scm
  - url
- Enable `sourcesJar`.
- Enable `javadocJar`.
- Validate `MavenPublication` via local publish only.
- Signing: optional preparation only, or leave disabled.
- `pyloros-app` must not be treated as library publication.
- Fat JAR remains application artifact from `pyloros-app`.

## Acceptance Criteria

- `gradlew.bat clean build` is green.
- `gradlew.bat publishToMavenLocal` works for library modules only.
- `pyloros-server` produces `jar`, `sourcesJar`, `javadocJar`.
- `pyloros-upstream-idea` produces `jar`, `sourcesJar`, `javadocJar`.
- `pyloros-app` still builds fat JAR.
- No remote publish occurs.
- No secrets committed.

## Not Allowed

- No remote publish (`publish`, Sonatype/Central portal tasks, etc.).
- No runtime behavior changes (OAuth, MCP, IDEA forwarding).
- No broad refactoring.

## Commit Message

`Prepare Maven metadata for local publish`

## Final Report

Overwrite `docs/agent/report.md` completely.
Include:
- files changed
- publication metadata summary
- exact local publish commands executed
- generated artifact coordinates in `~/.m2`
- build result
- app fat JAR verification result
- exact commit hash
- whether push was performed
