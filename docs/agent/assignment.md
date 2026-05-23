010-B - Fix catalog consistency for /pyloros and /sse tool calls

Befund:
api_tool listet Tools wie pyloros__ping und intellij-index__ide_index_status.
api_tool.call_tool auf exakt diese gelisteten Tools liefert aber:
Tool not found: <toolName>

Ursache:
ToolRouter.callTool las den ToolCatalog-Snapshot synchron ohne vorheriges Refresh.
Wenn ChatGPT tools/call schickt, ohne zuvor in dieser Session tools/list aufgerufen
zu haben (z.B. weil der Connector die Tool-Liste aus dem OpenAI-Cache verwendet),
ist der Snapshot leer.

Ziel:
Ein Tool, das über tools/list sichtbar ist, muss unmittelbar danach über tools/call
aufrufbar sein – auch wenn tools/list nicht in derselben Session aufgerufen wurde.

Fix:
ToolRouter.callTool ruft vor dem Catalog-Lookup immer toolCatalog.listTools() auf
(was refresh() triggert und den Snapshot aktualisiert), bevor per dispatch()
im frisch befüllten Snapshot nachgeschlagen wird.

Akzeptanz:
1. Connector-Refresh zeigt pyloros__ping.
2. api_tool.call_tool pyloros__ping funktioniert.
3. Connector-Refresh zeigt intellij-index__ide_index_status.
4. api_tool.call_tool intellij-index__ide_index_status funktioniert.
5. Pyloros-Log zeigt beim Call:
   [MCP] ... method=tools/call ... name=intellij-index__ide_index_status
   [TOOL-ROUTER] catalog lookup externalName=intellij-index__ide_index_status hit=true
6. /pyloros und /sse nutzen exakt denselben ToolCatalog und ToolRouter.
7. RPC tools/call und path-based invocation nutzen denselben Resolver.
8. Kein getrennter Catalog pro Route.
9. Keine Änderung am Separator.
10. Build + clean build grün.

Kein Commit ohne Freigabe.
