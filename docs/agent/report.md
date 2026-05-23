Was wurde verifiziert, geändert oder implementiert?
- Analysiert wurde der Unterschied zwischen lokalem `tools/call` (JSON-RPC auf `/sse`) und Connector-/`api_tool`-ähnlicher Resource-Auflösung (POST auf `/sse/<toolName>`). Ursache für `Resource not found .../intellij-index/ide_index_status` war fehlendes HTTP-Routing für path-basierte Tool-Invocation.
- Implementiert wurde eine robuste HTTP-Kompatibilität im Pyloros-Layer:
  - zusätzlicher POST-Wildcard-Route-Mount für `/sse/*`
  - Resolver für Toolnamen aus dem Pfad (inkl. URL-Decoding, Slash bleibt Bestandteil des Namens)
  - Fallback-Mapping für RPC-Requests ohne `params.name` auf den Toolnamen aus dem Pfad
  - direkte path-basierte Invocation ohne `method` wird auf `ToolRouter` gemappt
- Kanonische Toolnamen wurden nicht geändert (`provider/tool` bleibt unverändert), Routing erfolgt weiterhin über `ToolCatalog`/`ToolRouter`.
- Konfliktprüfung: Kein inhaltlicher Konflikt zur Assignment-Datei beim Routing-Teil; Implementierung bleibt mit den Assignment-Regeln „exact key routing / kein Prefix-Split“ konsistent.

Welche Dateien wurden geändert oder neu erstellt?
- Geändert: `pyloros-server/src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
- Neu: `pyloros-server/src/main/java/com/aresstack/pyloros/http/ToolCallRequestResolver.java`
- Neu/Geändert: `pyloros-server/src/test/java/com/aresstack/pyloros/http/ToolCallRequestResolverTest.java`
- Geändert: `docs/agent/report.md`

Welche Architekturentscheidung wurde berührt?
- HTTP-Transport-Kompatibilität wurde erweitert, ohne die zentrale Architektur zu ändern:
  - Tool-Aggregation bleibt über `ToolProvider`/`ProviderRegistry`/`ToolCatalog`
  - Tool-Invocation bleibt über `ToolRouter`
  - Slash-Namen bleiben kanonisch und werden als exakte Namen behandelt, nicht als Resource-Hierarchie.

Welche Tests, Builds und Runtime-Checks wurden ausgeführt?
- Build:
  - `gradlew.bat clean build --no-daemon --console=plain` (mit Java 21) → erfolgreich.
- Unit-Tests:
  - Neue Tests in `ToolCallRequestResolverTest` liefen im Gradle-Build mit.
- Reproduzierbarer lokaler Invocation-Check für betroffenen Toolnamen:
  1. Pyloros lokal gestartet (mit `OAUTH_ACCESS_TOKEN=smoke-token`).
  2. `tools/list` auf `http://127.0.0.1:8081/sse` erfolgreich (HTTP 200).
  3. Path-basierte Invocation auf `http://127.0.0.1:8081/sse/intellij-index/ide_index_status` mit JSON-Body `{"arguments":{}}` erfolgreich (HTTP 200, `isError=false`).
  4. Gegencheck klassisch per JSON-RPC `tools/call` mit `name=intellij-index/ide_index_status` ebenfalls erfolgreich (HTTP 200, `isError=false`).

Result: successful

If failed: exact error and recommended next step
- Nicht zutreffend.

Exact commit hash, or No commit created
- No commit created
- Current HEAD reference: `54a70cc3e18a542a95fec42a116af43e78e55f41`
