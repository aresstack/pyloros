Was wurde verifiziert, geändert oder implementiert?
- E2E-Diagnostik für den Unterschied "gelistet != aufrufbar" umgesetzt, damit klar trennbar ist:
  - (A) Call erreicht Pyloros nicht (kein passender MCP-POST-/tools/call-Logeintrag)
  - (B) Call erreicht Pyloros, scheitert im Routing/Dispatch (Router-/Provider-Logs zeigen Hit/Miss und Ziel)
- `McpRoutes` erweitert:
  - MCP-POST-Logging mit `path`, `method`, `source` (`rpc`/`path`), `resolvedToolName`
  - `tools/call`-Detail-Logging mit `name` und `argumentKeys` (nur Keys, keine Secret-Werte)
- `ToolRouter` erweitert:
  - exakter Catalog-Lookup-Log `externalName`, `hit=true/false`
  - bei Hit zusätzlich `providerId` und `upstreamToolName`
  - Dispatch-Log vor Provider-Aufruf
- `GenericMcpToolProvider` Dispatch-Logs präzisiert auf `providerId` + `upstreamToolName`.
- Routingsemantik unverändert belassen:
  - externer Name bleibt `provider/tool`
  - Provider erhält weiterhin nur `upstreamToolName`.

Welche Dateien wurden geändert oder neu erstellt?
- Geändert: `pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
- Geändert: `pyloros-server/src/main/java/com/aresstack/pyloros/tool/ToolRouter.java`
- Geändert: `pyloros-server/src/main/java/com/aresstack/pyloros/upstream/mcp/GenericMcpToolProvider.java`
- Geändert: `pyloros-server/src/test/java/com/aresstack/pyloros/http/ToolCallRequestResolverTest.java`
- Geändert: `pyloros-server/src/test/java/com/aresstack/pyloros/tool/ToolCatalogRoutingTest.java`
- Geändert: `docs/agent/report.md`

Welche Architekturentscheidung wurde berührt?
- Keine neue Architektur; bestehende Entscheidung bestätigt:
  - Routing über exakten Toolnamen via `ToolCatalog`/`ToolRouter`
  - kein Prefix-/Split-Routing
  - Slash bleibt kanonischer Bestandteil des Toolnamens
  - Provider-API bleibt `callTool(upstreamToolName, args)`.

Welche Tests, Builds und Runtime-Checks wurden ausgeführt?
- Tests:
  - `:pyloros-server:test --tests com.aresstack.pyloros.http.ToolCallRequestResolverTest --tests com.aresstack.pyloros.tool.ToolCatalogRoutingTest` -> erfolgreich.
- Build:
  - `build` mit Java 21 (`JAVA_HOME=C:\Program Files\Zulu\zulu-21`) -> erfolgreich.
  - `clean build` mit Java 21 -> fehlgeschlagen wegen Dateisperre beim Löschen von `pyloros-app/build/libs/pyloros.jar`.
- Lokale Verifizierbarkeit mit neuer Diagnosekette:
  - Für einen `tools/call` oder `POST /sse/<provider/tool>` sind jetzt die geforderten Logs für Nameauflösung, Catalog-Hit/Miss und Upstream-Dispatch vorhanden.

Result: failed

If failed: exact error and recommended next step
- Exakter Build-Fehler bei `clean build`:
  - `Execution failed for task ':pyloros-app:clean'.`
  - `java.io.IOException: Unable to delete directory 'C:\Projects\pyloros\pyloros-app\build'`
  - Ursache: `pyloros-app/build/libs/pyloros.jar` war gelockt (offener Prozess/Handle).
- Zudem ist die zentrale Akzeptanz (echter ChatGPT/api_tool-End-to-End-Call) noch nicht final nachgewiesen.
- Empfohlene nächste Schritte:
  1. Laufenden Pyloros-/Java-Prozess beenden, der `pyloros.jar` lockt.
  2. `clean build` erneut ausführen.
  3. ChatGPT Connector "Aktualisieren" und den echten `api_tool.call_tool` auf `intellij-index/ide_index_status` ausführen.
  4. Pyloros-Logs prüfen:
     - Wenn kein `[MCP] tools/call ... name=intellij-index/ide_index_status` erscheint -> Fehler vor Pyloros (Connector-Mapping).
     - Wenn Log erscheint und danach `hit=false`/Dispatch-Fehler -> Fehler in Pyloros-Routing/Upstream.

Exact commit hash, or No commit created
- No commit created
- Current HEAD reference: `5f48f2be9137b96ea854c33b36efd93b8df97c7b`
