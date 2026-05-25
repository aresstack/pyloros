# Report — Issue #20 (R4-03: Plugin-Konfiguration und Enable/Disable)

## Was wurde umgesetzt?

Self-contained Plugin-Konfigurations-Slice im Modul `pyloros-server` unter dem
neuen Package `com.aresstack.pyloros.plugin`:

- Konfigurationsmodell (`PluginsConfig`, `PluginEntry`) passend zum JSON-Beispiel
  der Issue (`plugins.enabledByDefault`, `plugins.items[].id|enabled|configuration`).
- `PluginConfiguration` als unveränderlicher Lese-View pro Plugin mit typisierten
  optionalen Gettern (`getString/Int/Boolean(...)`) und Required-Gettern
  (`requireString/Int/Boolean(...)`), die bei Fehlen oder Typfehler eine
  `PluginConfigurationException` mit `pluginId` und `configurationKey` werfen.
- Minimaler `PluginContext` entfällt in dieser PR — die kanonische
  `PluginContext`-Definition wird laut R4-04 (Issue #21) dort eingeführt.
  `PluginConfiguration` ist so gestaltet, dass sie vom dortigen Context-Owner
  als Konfigurations-View weitergereicht werden kann.
- `PluginActivationResolver`: leitet pro Plugin-ID eine `PluginActivation`
  (enabled + diagnostischer `reason`-Code + Konfiguration) ab. Explizites
  `enabled` schlägt `enabledByDefault`. Duplicate IDs werden als
  `PluginConfigurationException` gemeldet.
- `PluginsConfigLoader`: toleranter Jackson-basierter Parser. Fehlender oder
  leerer `plugins`-Block ergibt `PluginsConfig.empty()` (kein Serverabbruch);
  strukturelle JSON-Fehler werden als `PluginConfigurationException` gemeldet.

Tests decken alle in der Issue geforderten Szenarien ab:
- Plugin enabled / disabled (`PluginActivationResolverTest`)
- `enabledByDefault=true` / `false` (`PluginActivationResolverTest`)
- Plugin-spezifische Konfiguration wird durchgereicht (`PluginActivationResolverTest`,
  `PluginConfigurationTest`)
- Fehlende optionale Konfiguration führt nicht zum Abbruch (`PluginConfigurationTest`)
- Ungültige/fehlende Pflicht-Konfiguration und ungültige JSON werden diagnostiziert
  (`PluginConfigurationTest`, `PluginsConfigLoaderTest`)

## Geänderte / neue Dateien

Neu (`pyloros-server/src/main/java/com/aresstack/pyloros/plugin/`):
- `PluginsConfig.java`
- `PluginEntry.java`
- `PluginConfiguration.java`
- `PluginConfigurationException.java`
- `PluginActivation.java`
- `PluginActivationResolver.java`
- `PluginsConfigLoader.java`

Neu (`pyloros-server/src/test/java/com/aresstack/pyloros/plugin/`):
- `PluginActivationResolverTest.java` (8 Tests)
- `PluginConfigurationTest.java` (6 Tests)
- `PluginsConfigLoaderTest.java` (6 Tests)

Aktualisiert:
- `docs/agent/report.md` (diese Datei).

Nicht geändert: `PylorosApplication`, bestehende Konfigurations-Records,
Provider-/ToolCatalog-Code. Die Verdrahtung in den Server-Bootstrap erfolgt in
R4-04 / R4-05 zusammen mit `ServiceLoaderPluginRegistry`.

## Berührte Architekturentscheidung

Plugins gehen weiterhin über `PylorosPlugin → PluginContext → ToolProvider →
ProviderRegistry → ToolCatalog`. Der hier eingeführte `PluginContext` bleibt
absichtlich klein und enthält **keine** HTTP-, Vert.x- oder JSON-RPC-Typen
(konform mit der Plugin-API-Regel aus Issue #17/#18).

## Ausgeführte Prüfungen

- `./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.plugin.*"` → **BUILD SUCCESSFUL**
- `./gradlew --no-daemon :pyloros-server:test` (volle Server-Test-Suite) → **BUILD SUCCESSFUL** (keine Regressionen)

## Ergebnis

Erfolgreich. Alle Akzeptanzkriterien aus Issue #20 sind durch Code + Tests abgedeckt.

## Commit

Erstellt durch `report_progress`, siehe PR #45.
