# Report — Issue #23 (R4-06: Plugin-Fehlerbehandlung und Diagnose)

## Was wurde umgesetzt / verifiziert?

- PR auf aktuelles `main` gemerged (kanonische R4-01-API aus #46 und
  R4-03-Config aus #45 sind dort jetzt vorhanden). Diese PR liefert nur
  noch die R4-06-Diagnoseschicht oben drauf — keine duplizierten
  API-Dateien mehr.
- R4-06-eigene Typen in `com.aresstack.pyloros.plugin` (`pyloros-server`):
  - `PluginStatus` (`LOADED`, `DISABLED`, `FAILED_TO_LOAD`,
    `FAILED_TO_INITIALIZE`, `FAILED_TO_CONTRIBUTE`); Javadoc verweist
    jetzt explizit auf die kanonischen `initialize(PluginContext)` und
    `contribute(PluginContext)` Hooks.
  - `PluginErrorInfo` (Record) — Fehlerklasse + auf 500 Zeichen
    gekuerzte Meldung (`...`-Suffix).
  - `PluginLoadResult` (Record) — Host-Sicht auf einen entdeckten
    Plugin: `pluginId`, `status`, optionaler kanonischer
    `PluginDescriptor`, optionale `PluginErrorInfo`. Faellt bei
    nicht instanziierbaren Plugins auf den Implementations-
    klassennamen zurueck.
  - `PluginRegistry` — laedt via `ServiceLoader.stream()`, kapselt jede
    Phase (Instanziierung, `descriptor()`, `initialize(context)`,
    `contribute(context)`) einzeln in `try/catch (Throwable)`,
    nutzt `PluginContext.noop(pluginId)` als Lifecycle-Kontext,
    validiert die `PluginContribution` (nicht-blank `providerId`,
    keine Duplikate) und veroeffentlicht beigetragene Provider nur
    atomar: jeder Fehler verwirft die *gesamte* Contribution.
    Disabled IDs werden vor `initialize(context)` kurzgeschlossen.
- `PylorosApplication` wird in diesem PR nicht verdrahtet. Die finale
  Integration mit dem R4-03-Aktivierungsmodell aus `main` erfolgt in
  einer Folge-PR; dieser PR bleibt strikt auf den kanonischen Typen.
- Tests: `PluginRegistryTest` deckt erfolgreiches Laden, Disabled,
  Konstruktor-Fehler, `initialize()`-Fehler, `contribute()`-Fehler,
  ungueltige Contributions (atomar verworfen), Mix faulty/healthy
  und Truncation langer Fehlermeldungen ab.

## Geaenderte / neue Dateien (relativ zu `main`)

- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginStatus.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginErrorInfo.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginLoadResult.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginRegistry.java`
- Neu: `pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginRegistryTest.java`
- Geaendert: `docs/agent/report.md` (dieser Report).

Keine Aenderungen an den kanonischen API-Dateien aus #46
(`PylorosPlugin`, `PluginContext`, `PluginDescriptor`,
`PluginContribution`, `PluginContributionResult`, `package-info.java`,
`PluginApiTest`) — diese kommen unveraendert aus `main`.

## Architekturentscheidung beruehrt?

- R4-01-API aus #46 bleibt unangetastet; R4-06 ergaenzt rein additiv
  um Diagnose-Status, Fehlerinfo, Failure-Isolation und atomare
  Contribution-Publikation.
- `PylorosApplication` bleibt unveraendert, bis eine Folge-PR die
  Enable/Disable-Konfiguration aus #45 mit der Registry verdrahtet.

## Tests / Builds

- `./gradlew --no-daemon :pyloros-server:test --tests com.aresstack.pyloros.plugin.PluginRegistryTest` → BUILD SUCCESSFUL.
- `./gradlew --no-daemon :pyloros-server:test` (volle Server-Suite) → BUILD SUCCESSFUL.

## Ergebnis

Erfolgreich.

## Commit

Wird vom Cloud-Agent (Copilot) via `report_progress` gepusht; konkreter
Hash siehe PR-Branch `copilot/r4-06-plugin-error-handling`.
