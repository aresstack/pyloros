Was wurde verifiziert, geändert oder implementiert?
- Verifiziert, dass die 009-F-Umstellung in der Workspace-Basis bereits weitgehend vorhanden ist: Pyloros lädt Upstreams datengetrieben aus der Copilot-`mcp.json`, nutzt den Default-Pfad `%LOCALAPPDATA%\github-copilot\intellij\mcp.json`, behält Slash-Toolnamen kanonisch und routet lokal erfolgreich über die Map-basierte Toolauflösung.
- Geändert wurden die `mcp.json`-DTOs, damit die reale Copilot-Datei mit zusätzlichen stdio-/`command`-Feldern robust eingelesen wird, ohne den Start zu blockieren. Konkret ignorieren `McpJsonConfig`, `McpServerConfig` und `RequestInitConfig` nun unbekannte JSON-Felder.
- Verifiziert, dass Start ohne `--mcp-config`, ohne `-Dmcp.config` und ohne Upstream-spezifische ENV-Variablen den Copilot-Defaultpfad auf diesem System findet: `C:\Users\angel\AppData\Local\github-copilot\intellij\mcp.json`.
- Verifiziert, dass nicht aggregierbare Copilot-Einträge ohne URL (z. B. stdio-Server) sauber übersprungen werden und HTTP/SSE-Upstreams weiterhin registriert werden.
- Verifiziert, dass `tools/list` und `tools/call` mit Slash-Namen funktionieren, inklusive `intellij/get_project_modules`, `github/get_me` und `intellij-index/ide_index_status`.

Welche Dateien wurden geändert oder neu erstellt?
- Geändert: `docs/agent/assignment.md`
- Geändert: `pyloros-app/src/main/java/com/aresstack/pyloros/config/McpJsonConfig.java`
- Geändert: `pyloros-app/src/main/java/com/aresstack/pyloros/config/McpServerConfig.java`
- Geändert: `pyloros-app/src/main/java/com/aresstack/pyloros/config/RequestInitConfig.java`
- Geändert: `docs/agent/report.md`

Welche Architekturentscheidung wurde berührt?
- MCP-Upstream-Discovery bleibt datengetrieben über die Copilot-`mcp.json` statt über ein separates Pyloros-Konfigurationsuniversum.
- Der Parser ist jetzt absichtlich tolerant gegenüber zusätzlichen Copilot-Serverfeldern, damit optionale/nicht aggregierbare Serverdefinitionen die Aggregation von HTTP-/SSE-Upstreams nicht verhindern.
- Die bestehende exakte Map-Routing-Architektur für kanonische Slash-Namen wurde verifiziert und beibehalten.

Welche Tests, Builds und Runtime-Checks wurden ausgeführt?
- Build: `./gradlew.bat clean build --no-daemon`
- Fat Jar: `./gradlew.bat :pyloros-app:shadowJar --no-daemon`
- Rebuild nach Parser-Anpassung: `./gradlew.bat :pyloros-app:build --no-daemon`
- Runtime-Check: Start von `pyloros-app/build/libs/pyloros.jar` mit nur `OAUTH_ACCESS_TOKEN=smoke-token`; im Log verifiziert:
  - `[MCP-CONFIG] loaded mcp.json path=C:\Users\angel\AppData\Local\github-copilot\intellij\mcp.json`
  - `github`, `intellij`, `intellij-index` wurden registriert
  - zusätzliche Copilot-stdio-Einträge wurden übersprungen statt den Start zu blockieren
- Smoke-Test: `./gradlew.bat :pyloros-app:runMcpAggregationSmokeTest --no-daemon`
  - Ergebnis: 8/8 Checks erfolgreich
  - Enthalten: `tools/list`, `pyloros__ping`, `intellij/get_project_modules`, `github/get_me`, `intellij-index/ide_index_status`

Result: successful

If failed: exact error and recommended next step
- Nicht zutreffend. Zwischenzeitlich trat beim ersten Runtime-Check eine `UnrecognizedPropertyException` für das Feld `command` in der realen Copilot-`mcp.json` auf; nach dem Ignorieren unbekannter JSON-Felder ist der Start erfolgreich und der Smoke-Test grün.

Exact commit hash, or No commit created
- No commit created
- Current HEAD reference: `a6e7ddfe3f99e8bf7bc1382bb750ddb80ccd5bd1`
