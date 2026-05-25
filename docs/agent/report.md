# Report — Issue R4-01 (Plugin API Contracts definieren)

## Was wurde umgesetzt?

Eine stabile, Java-21-kompatible Plugin-API unter dem Paket
`com.aresstack.pyloros.plugin` im Modul `pyloros-server`:

- `PylorosPlugin` — Schnittstelle, die ein Plugin implementiert. Liefert
  Deskriptor und Beitrag.
- `PluginDescriptor` — Record mit `id`, `name`, `version`, `description`.
  Validiert eine fehlende/leere Plugin-ID (`NullPointerException` bzw.
  `IllegalArgumentException`).
- `PluginContribution` — Record, das aktuell `List<ToolProvider>` bündelt.
  Defensiv kopiert und unterstützt `empty()` sowie `ofToolProviders(...)`.
- `PluginContributionResult` — Record für das Pro-Plugin-Ergebnis eines
  Hosts (`accepted` / `rejected` mit Begründung). Damit lässt sich z. B.
  „doppelte Plugin-ID“ später erkennbar machen.
- `package-info.java` mit Javadoc als Paketdokumentation.

Die API enthält keine Vert.x-, HTTP- oder JSON-RPC-Abhängigkeiten.
`ToolProvider` wird ausschließlich als bestehender Port referenziert.

## Geänderte/neue Dateien

- `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PylorosPlugin.java` (neu)
- `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginDescriptor.java` (neu)
- `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginContribution.java` (neu)
- `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/PluginContributionResult.java` (neu)
- `pyloros-server/src/main/java/com/aresstack/pyloros/plugin/package-info.java` (neu)
- `pyloros-server/src/test/java/com/aresstack/pyloros/plugin/PluginApiTest.java` (neu)
- `docs/agent/report.md` (überschrieben)

## Architekturentscheidung

- Plugin-API liegt in `pyloros-server` (gleiches Modul wie `ToolProvider`),
  Paket `com.aresstack.pyloros.plugin`.
- Erweiterungspunkte bewusst klein: nur `ToolProvider` als Beitragstyp.
- Keine Bindung an Vert.x/HTTP/JSON-RPC in der API.
- `PluginContributionResult` macht Host-Entscheidungen (z. B. doppelte
  Plugin-ID) für künftige Aggregations-/Registry-Schichten erkennbar, ohne
  die Registry selbst in dieser Aufgabe zu definieren.
- `PylorosPlugin` selbst nutzt keinen `PluginContext`; das aus dem
  Requirements-Dokument bekannte Konstrukt wurde bewusst nicht
  vorgegriffen, weil das Issue Erweiterungspunkte klein halten möchte.

## Tests, Builds, Runtime-Checks

- `./gradlew --no-daemon :pyloros-server:test --tests
  'com.aresstack.pyloros.plugin.*'` → erfolgreich.
- `./gradlew --no-daemon :pyloros-server:test` → kompiliert sauber. Es
  besteht ein **vorhandener, nicht von dieser Aufgabe verursachter**
  Fehlschlag in `AcpIntegrationTest.testLargeOutput()` (74 von 75 Tests
  grün, der eine Fehler liegt außerhalb des Plugin-API-Codepfads).

## Ergebnis

Erfolgreich. Die Akzeptanzkriterien des Issues sind erfüllt:

- Plugin liefert eindeutige `id` (validiert, blanke IDs werden abgelehnt).
- Plugin liefert Metadaten (`name`, `version`, `description`).
- Plugin kann `ToolProvider` über `PluginContribution` beitragen.
- API ist Java-21 (Records, `List.of`, Optional) kompatibel.
- API ohne Vert.x-/HTTP-/JSON-RPC-Abhängigkeiten.
- API ist via Javadoc dokumentiert.
- Tests beweisen: Beispielimplementierung kompiliert, ToolProvider-Beitrag
  funktioniert, fehlende ID wird validiert, doppelte IDs sind über
  `PluginContributionResult` erkennbar.

## Commit

Wird durch `report_progress` erzeugt; SHA folgt im PR.
