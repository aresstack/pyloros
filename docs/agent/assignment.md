010-A - Rename public endpoint to /pyloros

Ziel:
Pyloros bekommt einen semantisch korrekten öffentlichen Gateway-Endpunkt.

Canonical endpoint:
- /pyloros

Legacy alias:
- /sse

Regeln:
- /pyloros und /sse müssen initialize, tools/list, tools/call, resources/list unterstützen
- /sse bleibt deprecated compatibility alias
- README ChatGPT Connector URL: https://current-car.com/pyloros
- Startlog zeigt: Pyloros listening ... public URL https://current-car.com/pyloros
- Logs markieren legacy alias:
  [MCP] post path=/sse ... deprecated=true
- keine Provider-spezifischen Public-Endpoints
- keine Änderung an ToolCatalog/ProviderRegistry/Routing
- Default Tool-Separator bleibt "__"

Umsetzung:
1. Öffentlichen MCP-Endpunkt zentral auf `/pyloros` setzen.
2. Legacy-Alias `/sse` zusätzlich mounten.
3. Beide Endpunkte müssen dieselben MCP-Operationen bedienen:
   - initialize
   - tools/list
   - tools/call
   - resources/list
   - prompts/list darf weiter unterstützt bleiben
4. Logging ergänzen:
   - Requests auf `/sse` als `deprecated=true`
   - Requests auf `/pyloros` als `deprecated=false`
5. Startlog und README auf `/pyloros` aktualisieren.
6. Keine Änderungen an ToolCatalog/ProviderRegistry/Routing-Semantik.

Akzeptanz:
- build grün
- alter Connector über /sse funktioniert weiter
- neuer Connector über /pyloros funktioniert
- api_tool.call_tool intellij-index__ide_index_status über neuen Connector funktioniert
- README und docs/agent/report.md aktualisiert
- kein Commit ohne Freigabe
