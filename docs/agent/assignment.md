009-G - Test resource-safe external tool names

Ziel:
Empirisch pruefen, ob ChatGPT/api_tool nur an Slash-Namen im Resource-Pfad scheitert.

Aenderung:
Externer Toolname wird temporaer von `provider/tool` auf `provider__tool` umgestellt.

Beispiele:
- intellij__get_project_modules
- github__get_me
- intellij-index__ide_index_status

Nicht aendern:
- ProviderRegistry
- ToolAddress
- Upstream tool names
- GenericMcpToolProvider
- mcp.json Loader
- Transports

Architekturgrenzen:
- Nur externe `tools/list`-Benennung aendern.
- Intern bleibt alles map-basiert und exakt:
  - ToolAddress(providerId, upstreamToolName)
  - Router: toolsByExternalName.get(requestToolName)
  - Provider: callTool(upstreamToolName, arguments)
- Kein split("/")
- Kein startsWith/prefix routing

Umsetzung:
1. Externen Name zentral im ToolCatalog/Name-Builder bilden als:
   externalName = providerId + "__" + upstreamToolName
2. Tests anpassen/erweitern:
   - Toolliste enthaelt die `__`-Namen
   - Routing mappt `intellij-index__ide_index_status` auf
     providerId=intellij-index und upstreamToolName=ide_index_status
   - Unbekannte Namen liefern sauberen Fehler
3. Logging-/Diagnosekette beibehalten.

Akzeptanz:
1. Build gruen.
2. Pyloros neu starten.
3. ChatGPT Connector aktualisieren.
4. Toolliste enthaelt:
   - intellij__get_project_modules
   - github__get_me
   - intellij-index__ide_index_status
5. api_tool.call_tool testbar auf:
   - /.../intellij__get_project_modules
   - /.../github__get_me
   - /.../intellij-index__ide_index_status
6. Pyloros-Log zeigt bei Erfolg:
   - [MCP] ... method=tools/call ... name=intellij-index__ide_index_status
   - [TOOL-ROUTER] catalog lookup ... hit=true
   - [TOOL-ROUTER] provider dispatch providerId=intellij-index upstreamToolName=ide_index_status

Hinweise:
- Das ist ein kontrollierter A/B-Test-Slice, keine dauerhafte Architekturentscheidung.
- Kein Commit ohne explizite Freigabe.
