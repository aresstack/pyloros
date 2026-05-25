# Report — Issue #23 (R4-06: Plugin-Fehlerbehandlung und Diagnose)

## Was wurde umgesetzt / verifiziert?

- PR wurde auf die kanonische Plugin-API aus #46 rebased. Die in der
  ersten Iteration eingefuehrte konkurrierende `PylorosPlugin`-Definition
  und der dort verwendete diagnostische `PluginDescriptor` wurden
  entfernt; statt dessen nutzt R4-06 die kanonischen Typen
  `PylorosPlugin` (mit `descriptor()` / `contribute()`),
  `PluginDescriptor` (Metadaten), `PluginContribution` und
  `PluginContributionResult`.
- R4-06-eigene Diagnose-Typen bleiben in diesem PR:
  - `PluginStatus`-Enum mit `LOADED`, `DISABLED`, `FAILED_TO_LOAD`,
    `FAILED_TO_INITIALIZE`, `FAILED_TO_CONTRIBUTE`.
  - `PluginErrorInfo` (Record) — Fehlerklasse + auf 500 Zeichen
    gekuerzte Meldung (mit `...`-Suffix).
  - `PluginLoadResult` (Record, vormals `PluginDescriptor`) — Host-Sicht
    auf einen entdeckten Plugin: `pluginId`, `status`, optionaler
    kanonischer `PluginDescriptor` und optionale `PluginErrorInfo`.
    Faellt bei nicht instanziierbaren Plugins auf den Implementations-
    klassennamen zurueck.
- `PluginRegistry` laedt Plugins via `ServiceLoader.stream()`, kapselt
  jede Phase (Instanziierung, `descriptor()`, `contribute()`) einzeln
  in `try/catch (Throwable)`, validiert die `PluginContribution`
  (nicht-blank `providerId`, keine Duplikate) und veroeffentlicht
  beigetragene Provider nur atomar: jeder Fehler verwirft die *gesamte*
  Contribution des betroffenen Plugins. Disabled IDs werden vor
  `contribute()` kurzgeschlossen.
- `PylorosApplication` wird in diesem PR nicht verdrahtet. Die finale
  Integration in den Bootstrap geschieht erst, wenn #45 (Aktivierungs-/
  Konfigurations-Modell) ebenfalls auf die kanonische API ausgerichtet
  ist; bis dahin bleibt R4-06 strikt auf den kanonischen Typen.
- Tests:
  - `PluginRegistryTest` deckt erfolgreiches Laden, Disabled,
    Konstruktor-Fehler, fehlerhafte `contribute()`-Implementierungen,
    ungueltige Contributions (atomar verworfen), Mix faulty/healthy
    sowie Truncation langer Fehlermeldungen ab.
  - `PluginApiTest` deckt die kanonischen API-Records (`PluginDescriptor`,
    `PluginContribution`, `PluginContributionResult`) ab.

## Geaenderte / neue Dateien

- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PylorosPlugin.java`
  (kanonische API aus #46).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginDescriptor.java`
  (kanonische Metadaten aus #46).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginContribution.java`
  (kanonische Contribution aus #46).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginContributionResult.java`
  (kanonisches Host-Outcome aus #46).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginStatus.java`
  (R4-06 Diagnose-Status).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginErrorInfo.java`
  (R4-06 Diagnose-Fehler).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginLoadResult.java`
  (R4-06 Diagnose-Record, ersetzt frueheren `PluginDescriptor`-
  Diagnose-Record).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginRegistry.java`
  (Loader + Diagnose).
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/package-info.java`.
- Neu: `pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginRegistryTest.java`.
- Neu: `pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginApiTest.java`.

## Architekturentscheidung beruehrt?

- Kanonische R4-Plugin-API aus #46 wird ohne Konflikt uebernommen;
  R4-06 ergaenzt diese rein additiv um Diagnose-Status, Fehlerinfo,
  Failure-Isolation und atomare Contribution-Publikation.
- `PylorosApplication` bleibt unveraendert, bis #45 fuer die
  Enable/Disable-Konfiguration nachgezogen ist.

## Tests / Builds

- `./gradlew --no-daemon :pyloros-server:test` → BUILD SUCCESSFUL.

## Ergebnis

Erfolgreich.

## Commit

Wird vom Cloud-Agent (Copilot) via `report_progress` gepusht; konkreter
Hash siehe PR-Branch `copilot/r4-06-plugin-error-handling`.
