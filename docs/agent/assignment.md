# Current Assignment

Task: 007 - Fat Shadow JAR Runtime Artifact

Read AGENTS.md first. Use this file as the single source of truth.

## Correction / PO Decision

The previous direction toward Gradle application distributions and runtime scripts was wrong.

The product decision is explicit:

Pyloros must be built as a normal Java application artifact: a runnable fat Shadow JAR.

Runtime means:

```text
java -jar build/libs/<pyloros-shadow-or-all>.jar
```

No operator scripts are required for this task. Existing scripts from earlier work must not be expanded and should not be the runtime target for this task.

## Goal

Produce one runnable fat JAR that contains Pyloros and all required runtime dependencies.

## Scope

1. Configure the Gradle Groovy build to use the Shadow plugin.
2. Produce a runnable fat JAR with all runtime dependencies included.
3. Set the correct Main-Class manifest entry.
4. The expected command must work:

```powershell
java -jar build\libs\pyloros-*.jar
```

5. Prefer a stable artifact name if practical, for example:

```text
build/libs/pyloros.jar
```

or document the exact generated name.

6. Keep Java 21 requirement.
7. Keep existing `application.properties` defaults.
8. Keep environment variable support unchanged.
9. Keep Java system property support if already present; do not overbuild a SettingsRegistry in this task.
10. Do not add or expand runtime scripts.
11. Do not implement a Windows service.
12. Do not implement Gradle `installDist` as the runtime target.
13. Do not implement Docker, NSSM, scheduled tasks, systemd, or a watchdog.
14. Do not change OAuth semantics.
15. Do not change IDEA tool forwarding.

## Optional Config Boundary

If already simple and safe, support an optional external properties file, for example:

```text
config/pyloros.properties
```

But this is secondary. The main deliverable is the runnable fat Shadow JAR.

Configuration priority should remain clear:

```text
1. classpath defaults
2. optional external properties when present, if implemented
3. environment variables
4. Java system properties, if implemented
```

Secrets should stay in environment variables or local uncommitted files, not committed configs.

## Acceptance Criteria

- `gradlew.bat clean build` is green.
- `gradlew.bat clean shadowJar` succeeds.
- A runnable fat JAR is produced under `build/libs/`.
- `java -jar <fat-jar>` starts Pyloros.
- `GET /health` works after starting the fat JAR.
- Public connector still works after starting the fat JAR.
- `intellij/get_project_modules` works after starting the fat JAR.
- No runtime data or secrets are committed.
- README documents the fat JAR build and start command.

## Not Allowed

- No new runtime/operator scripts.
- No Windows service.
- No NSSM.
- No scheduled task setup.
- No Docker.
- No Linux/systemd.
- No watchdog or second JVM launcher.
- No `installDist` runtime target.
- No broad refactoring.

## Commit Message

`Build runnable fat jar`

## Final Report

Overwrite `docs/agent/report.md` completely.

Include:

- Files changed
- Shadow plugin version used
- Exact jar path
- Exact `java -jar` command tested
- Build result
- Healthcheck result
- Public connector verification result
- Exact commit hash
- Whether push was performed
