# Issue #43 — Prepare Maven Central publishing via GitHub Actions

## Ziel

Veroeffentlichung auf Maven Central via GitHub Actions vorbereiten,
analog zu `../win-directml-java`. Es wird **noch kein Release**
durchgefuehrt — der Workflow soll spaeter durch einen Tag-Push
(`v<x.y.z>`) ausgeloest werden koennen.

## Architekturregeln (aus Issue #43)

- Pyloros-interne `project(":pyloros-*")`-Abhaengigkeiten duerfen im
  Multi-Project-Build bleiben. Im veroeffentlichten POM muessen daraus
  `com.aresstack:<module>:<version>`-Eintraege werden.
- Externe AresStack-Abhaengigkeiten (anderer Repos, z. B.
  `win-directml-java`) duerfen im POM nur als feste Maven-Central-
  Koordinaten erscheinen. **Aktueller Stand: Pyloros hat solche
  Abhaengigkeiten nicht.**
- `./gradlew clean build` muss lokal weiter funktionieren.
- `./gradlew clean publishToMavenLocal -PreleaseBuild=true` muss
  Maven-Central-kompatible POMs erzeugen.

## Akzeptanz

1. Publishing-Setup analog zu `win-directml-java` (gradleup nmcp +
   nmcp.aggregation, Sonatype Central Portal, Signing in-memory).
2. Java 21 bleibt Standard.
3. Publishable Pyloros-Subprojects sind explizit festgelegt.
4. `./gradlew clean build` funktioniert lokal weiter.
5. `./gradlew clean publishToMavenLocal -PreleaseBuild=true` erzeugt
   gueltige POMs.
6. POMs enthalten kein `unspecified`, `latest`, keine lokalen Pfade.
7. Pyloros-interne Deps erscheinen als `com.aresstack:<module>:<version>`.
8. Externe AresStack-Deps haetten feste Maven-Koordinaten (kein Fall
   heute, aber Mechanik vorbereitet via `releaseBuild`-Schalter).
9. GitHub-Actions-Workflow fuer Tag-Push und manuellen Dispatch.
10. Benoetigte Secrets sind dokumentiert (auf Org-Ebene `aresstack`
    bereits konfiguriert und vererbt).
11. Release-Reihenfolge dokumentiert.

Kein Commit ohne Freigabe.
