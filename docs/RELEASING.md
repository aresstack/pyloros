# Releasing Pyloros to Maven Central

Pyloros releases are published by GitHub Actions through the Sonatype Central
Portal. Do not run a release from a developer machine.

## Published modules

The Maven Central release publishes only the modules listed in root
`build.gradle` under `publishableModules`:

- `pyloros-security`
- `pyloros-server`
- `pyloros-auth-oauth`
- `pyloros-upstream-idea`
- `pyloros-upstream-github`
- `pyloros-upstream-intellij-index`
- `pyloros-intellij-mcp`

`pyloros-app`, `pyloros-example-plugin` and `pyloros-manager-agent` remain in
the multi-project build but are not Maven Central artifacts.

## Required secrets

The release workflow expects these GitHub Actions secrets. They are configured
at the `aresstack` organization level and inherited by the repository:

- `CENTRAL_USERNAME`: Sonatype Central Portal user-token username
- `CENTRAL_PASSWORD`: Sonatype Central Portal user-token password
- `GPG_PRIVATE_KEY`: ASCII-armored private signing key
- `GPG_PASSPHRASE`: passphrase for the signing key

Gradle receives them as `ORG_GRADLE_PROJECT_*` properties and uses in-memory
signing. Local `publishToMavenLocal` does not require these secrets.

## Local verification

Run these commands before creating a release tag:

```powershell
.\gradlew.bat clean build
.\gradlew.bat clean publishToMavenLocal -PreleaseBuild=true
.\gradlew.bat verifyReleasePoms -PreleaseBuild=true
```

The release POM check rejects `unspecified`, `latest`, `systemPath`, `file://`,
relative parent path leakage and dependency entries without versions.

Internal Pyloros project dependencies may stay as `project(":pyloros-*")` in
the source build. In generated POMs they must appear as
`com.aresstack:<module>:<version>`.

Pyloros currently has no external AresStack dependencies from other
repositories. If one is added later, keep local development wiring separate
from release wiring and switch release builds to fixed Maven Central
coordinates with `-PreleaseBuild=true`.

## Release order

1. Make sure the release branch is green and the local verification commands
   above pass.
2. Confirm `build.gradle` `publishableModules` matches the artifacts intended
   for this release.
3. Create and push a version tag:

```powershell
git tag v<x.y.z>
git push origin v<x.y.z>
```

4. Watch the `Release to Maven Central` GitHub Actions workflow.
5. Confirm the Sonatype Central Portal deployment succeeds and artifacts are
   visible on Maven Central.

The workflow can also be started manually with `workflow_dispatch` by entering
the version without the leading `v`. Manual dispatch is intended for
controlled reruns; a normal release should use a tag push.

## CI command

The GitHub Actions release job runs:

```bash
./gradlew \
  -Pversion=<x.y.z> \
  -PreleaseBuild=true \
  --no-daemon \
  -x test \
  verifyReleasePoms \
  publishAggregationToCentralPortal
```
