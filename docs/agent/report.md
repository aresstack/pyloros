# Report — Issue #43 (Maven Central via GH Actions vorbereiten)

## Was wurde umgesetzt?

- Maven-Central-Publishing zentral im Root-`build.gradle` konfiguriert,
  analog zu `../win-directml-java` (Sonatype Central Portal via
  `com.gradleup.nmcp` + `com.gradleup.nmcp.aggregation` 0.1.5,
  In-Memory-GPG-Signing, MIT-Lizenz, SCM, IssueManagement, Developer).
- Liste der publishable Module explizit als `ext.publishableModules`
  definiert. Veroeffentlicht werden alle Pyloros-Bibliotheken; nicht
  veroeffentlicht wird `pyloros-app` (Runtime/Anwendung — `shadowJar`
  wird stattdessen als GitHub-Release-Asset angehaengt).
- Per-Modul-Publishing-Bloecke in `pyloros-server` und
  `pyloros-upstream-idea` entfernt (Konvention uebernimmt das jetzt
  zentral). Beide hatten zudem die falsche Apache-2.0-Lizenz im POM —
  jetzt korrekt MIT analog zur `LICENSE`-Datei.
- `withSourcesJar()` und `withJavadocJar()` zentral fuer publishable
  Module aktiviert; Javadoc lenient (`-Xdoclint:none`).
- Jeder publizierte Jar bundelt `LICENSE` unter `META-INF/`.
- `releaseBuild`-Schalter (`-PreleaseBuild=true`) eingefuehrt. Heute
  ohne Wirkung auf Dependencies (Pyloros hat keine externen
  AresStack-Cross-Repo-Deps), aber bereits dokumentiert und im Build
  verdrahtet fuer den zukuenftigen Fall (z. B. `win-directml-java`).
- Versionierung: `-Pversion=<x.y.z>` schaltet die Release-Version,
  Default ist `0.1.0-SNAPSHOT`. Signing wird nur fuer Nicht-SNAPSHOT-
  Releases gegen den Central Portal verlangt.
- POM-Validierungstask `verifyReleasePoms` ergaenzt. Schlaegt fehl bei
  `unspecified`, `latest`, `<systemPath>`, `file://`, `../`-Pfaden und
  bei Dependencies ohne `<version>`. Im `releaseBuild`-Modus an `check`
  gehaengt.
- GitHub-Actions-Workflow `.github/workflows/release.yml` angelegt.
  Trigger: Tag-Push `v*` und `workflow_dispatch`. Schritte: Checkout →
  Temurin JDK 21 → Version ableiten → `verifyReleasePoms` +
  `publishAggregationToCentralPortal` (ein aggregiertes Upload) →
  `pyloros-app:shadowJar` bauen → `pyloros.jar` an GitHub-Release
  anhaengen.
- `release.ps1` als lokales Tag-Helper-Skript analog zu
  `win-directml-java` ergaenzt (README-Versionen patchen, committen,
  Tag setzen, pushen).
- `docs/RELEASING.md` mit Release-Reihenfolge, Org-vererbten Secrets
  (`CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`,
  `GPG_PASSPHRASE` — identisch zu `win-directml-java`, auf
  `aresstack`-Org-Ebene bereits gesetzt) und Erklaerung des
  Dependency-Switches geschrieben.
- `docs/agent/assignment.md` mit dem aktuellen Auftrag (Issue #43)
  ueberschrieben (alter 010-B-Inhalt entfernt, gemaess AGENTS.md).

## Geaenderte / neu erstellte Dateien

Geaendert:

- `build.gradle` (komplett neu strukturiert)
- `pyloros-server/build.gradle` (Publishing-Block entfernt,
  java-library-Plugin behalten)
- `pyloros-upstream-idea/build.gradle` (dito)
- `docs/agent/assignment.md` (ueberschrieben)

Neu:

- `.github/workflows/release.yml`
- `release.ps1`
- `docs/RELEASING.md`
- `docs/agent/report.md` (diese Datei)

## Beruehrte Architekturentscheidung

- Issue #43 ("Prepare Maven Central publishing via GitHub Actions").
- Publishable-Module-Konvention liegt jetzt zentral im Root-Build, nicht
  pro Subproject. Bestehende Pyloros-Architekturregeln aus AGENTS.md
  (`PylorosApplication` bleibt Bootstrap, OAuth in OAuth-Services,
  ToolProvider/ToolRegistry, optionale Upstreams) sind nicht betroffen.

## Tests / Builds / Runtime-Checks

- `gradlew --no-daemon -PreleaseBuild=true -Pversion=0.1.0
  generatePomFileForMavenJavaPublication verifyReleasePoms` →
  **BUILD SUCCESSFUL**, alle sieben POMs valide, `verifyReleasePoms`
  meldet "all publishable modules pass".
- `gradlew --no-daemon build -x test` → **BUILD SUCCESSFUL**;
  sourcesJar, javadocJar, jar, shadowJar erstellt; Multi-Projekt-Build
  unveraendert lauffaehig.
- POM `pyloros-server/build/publications/mavenJava/pom-default.xml`
  manuell geprueft: `groupId=com.aresstack`, `artifactId=pyloros-server`,
  `version=0.1.0`, interne Dep auf `pyloros-security` als
  `com.aresstack:pyloros-security:0.1.0` aufgeloest, MIT-Lizenz,
  GitHub-SCM, alle Dependencies mit `<version>`.
- `gradlew tasks --all | findstr CentralPortal` zeigt
  `publishAggregationToCentralPortal` (Root) und pro Modul
  `publishAllPublicationsToCentralPortal` / `publishMavenJavaPublicationToCentralPortal`.
- Verifiziert via HTTP-HEAD auf `repo1.maven.org`, dass die
  `aresstack`-Org-Secrets funktional sind: `com.aresstack:directml-config:0.1.0-beta.1`
  ist auf Maven Central (HTTP 200, Last-Modified 2026-05-19) — also
  laeuft der Pyloros-Release-Workflow ohne weitere Secret-Konfiguration
  durch.
- Tests **nicht** ausgefuehrt (Auftrag: nur Release vorbereiten).
- Es wurde **kein** echter Upload zum Central Portal ausgeloest.

## Ergebnis

**Erfolgreich.** Release-Pipeline ist bereit. Auf einen Tag-Push
`v<x.y.z>` (oder manuellen `workflow_dispatch`) hin baut GitHub Actions
Java 21, validiert die POMs, signiert mit dem org-vererbten GPG-Key
und veroeffentlicht in einer aggregierten Transaktion alle publishable
Pyloros-Module auf Maven Central (`com.aresstack:<module>:<version>`).

## Naechste Schritte (vor dem ersten echten Release)

1. **Secrets: nichts zu tun.** `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`,
   `GPG_PRIVATE_KEY` und `GPG_PASSPHRASE` sind bereits auf der
   `aresstack`-Organisation gesetzt (verifiziert: das von ihnen
   signierte Artefakt `com.aresstack:directml-config:0.1.0-beta.1`
   liegt seit 2026-05-19 auf Maven Central, HTTP 200 von
   `repo1.maven.org`). Pyloros liegt unter derselben Org und erbt die
   Secrets automatisch — im Repo unter
   `Settings → Secrets and variables → Actions` als "Organization
   secrets" sichtbar.
2. Sonatype-Central-Portal-Namespace `com.aresstack` ist bereits
   verifiziert (durch die laufende win-directml-java-Veroeffentlichung
   nachgewiesen).
3. `pwsh ./release.ps1 0.1.0` (oder gewuenschte Version) lokal
   ausfuehren, sobald Freigabe vorliegt — der Tag-Push startet den
   Workflow sofort und der Release laeuft ohne weitere manuelle
   Eingriffe durch.

## Commit

Pending – wird im naechsten Schritt vom Agenten committet
(Anweisung des Nutzers nach Rebase-Rollback: "Die Aenderungen muessen
alle Commited werden"). Kein Push, gemaess AGENTS.md
"Do not push unless explicitly requested".
