# Report — Issue #23 (R4-06: Plugin-Fehlerbehandlung und Diagnose)

## Was wurde umgesetzt / verifiziert?

- Minimales Plugin-API in `pyloros-server` eingefuehrt, ausreichend um
  R4-06 isoliert umzusetzen (R4-01..R4-05 sind noch nicht implementiert,
  daher liefert dieses Issue den minimalen Tragebalken mit).
- `PylorosPlugin`-Interface mit drei klar getrennten Lifecycle-Phasen
  (load / initialize / contribute), `PluginContext` als zukunftssicherer
  Marker (R4-04 erweitert ihn spaeter).
- `PluginStatus`-Enum mit den im Issue geforderten Werten:
  `LOADED`, `DISABLED`, `FAILED_TO_LOAD`, `FAILED_TO_INITIALIZE`,
  `FAILED_TO_CONTRIBUTE`.
- `PluginErrorInfo` (Record) erfasst Fehlerklasse und gekuerzte Meldung
  (max. 500 Zeichen, mit `...`-Suffix). `PluginDescriptor` (Record)
  haelt `pluginId`, Status und optionale Fehlerinfo.
- `PluginRegistry` laedt Plugins via `ServiceLoader`, kapselt jede
  Phase einzeln in try/catch, validiert Contributions (kein `null`,
  kein leeres `providerId`, keine Duplikate) und veroeffentlicht
  Contributions nur, wenn die *gesamte* Plugin-Contribution gueltig ist.
  Faulty plugins koennen den Serverstart nicht abbrechen.
- `PylorosApplication` ruft die Registry beim Start auf, loggt
  Statuszeilen pro Plugin und reicht erfolgreich beigetragene
  `ToolProvider` an den bestehenden `ProviderRegistry`-/`ToolCatalog`-Flow weiter.
- Unit-Tests decken alle vom Issue geforderten Szenarien ab:
  erfolgreich geladen, deaktiviert, Konstruktor wirft, Init wirft,
  ungueltige Contribution, Truncation langer Fehlermeldungen sowie ein
  gemischter Mix-Test, der die Isolation faulty <-> healthy plugins
  zeigt.

## Geaenderte / neue Dateien

- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginContext.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PylorosPlugin.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginStatus.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginErrorInfo.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginDescriptor.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginRegistry.java`
- Neu: `pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginRegistryTest.java`
- Geaendert: `pyloros-app/src/main/java/com/aresstack/pyloros/PylorosApplication.java`
  (PluginRegistry-Verdrahtung + Diagnose-Logging).

## Architekturentscheidung beruehrt?

- Bestehender Architekturpfad
  `Plugin -> PylorosPlugin -> PluginContext -> ToolProvider contribution -> ProviderRegistry -> ToolCatalog`
  aus Issue #17 wird respektiert. Keine direkten Core-Zugriffe.
- `PylorosApplication` bleibt Bootstrap/Wiring; Lade- und Diagnose-Logik
  lebt in der `plugin`-Domain in `pyloros-server`.
- Optionaler Upstream/Plugin verhindert den Start nicht — fehlerhafte
  Plugins liefern Diagnoseeintrag, Server laeuft weiter.

## Tests / Builds

- `./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava :pyloros-app:compileJava` → BUILD SUCCESSFUL.
- `./gradlew --no-daemon :pyloros-server:test --tests com.aresstack.pyloros.plugin.PluginRegistryTest` → BUILD SUCCESSFUL.
- `./gradlew --no-daemon :pyloros-server:test` (volle Server-Suite) → BUILD SUCCESSFUL, keine Regression.

## Ergebnis

Erfolgreich.

## Commit

Wird vom Cloud-Agent (Copilot) via `report_progress` gepusht; konkreter
Hash siehe PR-Branch `copilot/r4-06-plugin-error-handling`.
