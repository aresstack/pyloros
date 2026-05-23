009-F - Use Copilot mcp.json as data-driven upstream config

Ziel:
Pyloros lädt MCP-Upstreams direkt aus der bestehenden Copilot-style mcp.json.
Keine eigene Pyloros-Upstream-Config.
Keine enabled Flags.
Keine PYLOROS_UPSTREAM_* ENV-Variablen.
Keine Properties pro MCP-Server.
Keine Magic Numbers.

Config-Datei:
mcp.json

Default-Pfad:
%LOCALAPPDATA%\github-copilot\intellij\mcp.json

Auf Angelo-System:
C:\Users\angel\AppData\Local\github-copilot\intellij\mcp.json

Pfad-Auflösung in dieser Reihenfolge:
1. --mcp-config=C:\path\to\mcp.json
2. -Dmcp.config=C:\path\to\mcp.json
3. %LOCALAPPDATA%\github-copilot\intellij\mcp.json
4. ./mcp.json
5. ./data/mcp.json

Wenn keine Datei gefunden wird:
Pyloros startet nur mit nativen Tools und loggt:
[MCP-CONFIG] no mcp.json found; starting without external MCP upstreams

Format:
{
  "servers": {
    "github": {
      "type": "http",
      "url": "https://api.githubcopilot.com/mcp/",
      "requestInit": {
        "headers": {
          "Authorization": "Bearer <TOKEN>"
        }
      }
    },
    "intellij-index": {
      "url": "http://127.0.0.1:29170/index-mcp/streamable-http"
    },
    "intellij": {
      "type": "sse",
      "url": "http://127.0.0.1:64343/sse"
    }
  }
}

Regeln:
- servers.<key> ist providerId
- type=sse -> SseMcpUpstreamClient
- type=http oder fehlender type mit http-url -> StreamableHttpMcpUpstreamClient
- requestInit.headers wird übernommen
- Header-Werte niemals loggen
- kein enabled
- wenn Server in mcp.json steht, wird er registriert
- wenn Server fehlt, existiert er nicht

Java-Datenstruktur:
record McpJsonConfig(Map<String, McpServerConfig> servers) {}

record McpServerConfig(
    String type,
    URI url,
    RequestInitConfig requestInit
) {}

record RequestInitConfig(
    Map<String, String> headers
) {}

ProviderRegistry:
Map<String, ToolProvider> providersById

ToolCatalog:
Map<String, ToolCatalogEntry> toolsByExternalName
Map<ToolAddress, ToolCatalogEntry> toolsByAddress

ToolAddress:
providerId
upstreamToolName

Routing:
requestToolName exakt als Key verwenden.

entry = toolsByExternalName.get(requestToolName)
provider = providersById.get(entry.address.providerId)
provider.callTool(entry.address.upstreamToolName, arguments)

Kein startsWith.
Kein Prefix-Gefrickel.
Kein split("/") als Routing-Wahrheit.
Slash bleibt Teil des Toolnamens.

Toolnamen:
intellij/get_project_modules
github/get_me
intellij-index/ide_index_status

README aktualisieren:
- Pyloros ist Headless Agent Capability Gateway
- mcp.json ist die Upstream-Konfiguration
- Default-Pfad ist %LOCALAPPDATA%\github-copilot\intellij\mcp.json
- optionaler Pfad per --mcp-config oder -Dmcp.config
- Copilot-style servers-Struktur
- type=sse und type=http
- requestInit.headers
- keine Secrets in Git
- keine Header-Werte im Log
- Slash-Namen provider/tool bleiben kanonisch
- ChatGPT Connector nach Änderungen aktualisieren

Akzeptanz:
- clean build grün
- fat jar baut
- Start ohne ENV findet Copilot mcp.json
- tools/list enthält Slash-Namen
- lokaler tools/call funktioniert mit Slash-Namen
- README.md aktualisiert
- docs/agent/report.md aktualisiert
- kein Commit ohne Freigabe
