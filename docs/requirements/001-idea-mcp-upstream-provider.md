# 001 - IDEA MCP Upstream Provider

## Ziel

Pyloros soll die Tools des lokalen JetBrains IDEA MCP Servers als eigenen ToolProvider einbinden. Danach soll ChatGPT nicht mehr nur das Dummy-Tool `pyloros__ping` sehen, sondern zusaetzlich die IDEA-Tools mit Prefix `idea__`.

## Kontext

Aktueller Stand:

- `PylorosApplication` startet Vert.x und mounted Routes.
- `McpRoutes` spricht nur mit `ToolRegistry`.
- `ToolRegistry` sammelt Tools aus `ToolProvider` Instanzen.
- `PylorosPingToolProvider` ist der erste Provider.
- Build ist aktuell gruen.

Naechster Architekturbaustein:

- `IdeaToolProvider`
- `IdeaMcpClient`
- `IdeaSseSession`
- `IdeaJsonRpcClient`

## Konfiguration

Erweitere `PylorosConfig` und `application.properties` um:

```properties
idea.mcp.enabled=true
idea.mcp.host=127.0.0.1
idea.mcp.port=64343
idea.mcp.sse.path=/sse
idea.mcp.connect.timeout.ms=3000
idea.mcp.response.timeout.ms=60000
idea.mcp.tool.prefix=idea__
```

Env-Overrides sollen analog zu den bestehenden Settings moeglich sein:

- `IDEA_MCP_ENABLED`
- `IDEA_MCP_HOST`
- `IDEA_MCP_PORT`
- `IDEA_MCP_SSE_PATH`
- `IDEA_MCP_CONNECT_TIMEOUT_MS`
- `IDEA_MCP_RESPONSE_TIMEOUT_MS`
- `IDEA_MCP_TOOL_PREFIX`

## Gewuenschte Pakete

```text
src/main/java/com/aresstack/pyloros/upstream/idea/
    IdeaMcpClient.java
    IdeaSseSession.java
    IdeaJsonRpcClient.java
    IdeaToolProvider.java
    IdeaMcpConfig.java
```

Optional, wenn es sauberer ist:

```text
src/main/java/com/aresstack/pyloros/infrastructure/jsonrpc/
    JsonRpcRequest.java
    JsonRpcResponse.java
```

## Verhalten: SSE-Verbindung

`IdeaSseSession` soll beim Start eine HTTP GET Verbindung zu folgendem Endpoint oeffnen:

```text
http://{host}:{port}{ssePath}
```

Default:

```text
http://127.0.0.1:64343/sse
```

Der IDEA MCP Server sendet ueber SSE ein Event `endpoint`, dessen `data` den POST-Endpunkt enthaelt, zum Beispiel:

```text
/message?sessionId=...
```

Dieser Pfad muss gespeichert werden. Spaetere JSON-RPC Requests an IDEA gehen per HTTP POST an:

```text
http://{host}:{port}{endpointFromSse}
```

Wenn die SSE-Verbindung beendet wird oder fehlschlaegt, soll nach ca. 2 Sekunden reconnectet werden.

## Verhalten: JSON-RPC zu IDEA

`IdeaJsonRpcClient` soll Requests an den gespeicherten POST-Endpunkt senden.

Mindestmethoden:

```text
initialize
notifications/initialized
tools/list
tools/call
```

Fuer den ersten Schritt reicht:

1. Warten, bis `endpointFromSse` vorhanden ist.
2. `initialize` senden.
3. `tools/list` senden.
4. Resultat cachen.
5. Bei Toolcalls `tools/call` an IDEA weiterreichen.

Wichtig: Die alte Node-Implementierung hatte nur einen Pending Call gleichzeitig. Fuer den ersten Java-Schritt ist das akzeptabel. Spaeter kann es ueber Request-IDs parallelisiert werden.

## Tool-Prefixing

Tools von IDEA sollen nach aussen mit Prefix `idea__` erscheinen.

Beispiel:

```text
execute_terminal_command
```

wird extern:

```text
idea__execute_terminal_command
```

Beim Aufruf muss `IdeaToolProvider` den Prefix entfernen, bevor er an IDEA weiterleitet.

## Tool-Schema

Die Tool-Definitionen von IDEA sollen moeglichst unveraendert bleiben, aber:

- `name` bekommt Prefix `idea__`.
- `securitySchemes` muss OAuth enthalten.
- `_meta.securitySchemes` muss OAuth enthalten.

OAuth-Schema:

```json
{
  "type": "oauth2",
  "scopes": ["mcp"]
}
```

## Fehlerverhalten

Wenn IDEA nicht erreichbar ist:

- Pyloros soll weiter starten.
- `tools/list` soll mindestens die anderen Provider liefern.
- Der IDEA Provider darf leere Toolliste liefern und den Fehler loggen.

Wenn ein IDEA Toolcall fehlschlaegt:

- MCP Result soll `isError=true` liefern.
- Fehlermeldung soll als Text-Content sichtbar sein.

## Akzeptanzkriterien

- Projekt baut erfolgreich.
- `pyloros__ping` bleibt sichtbar.
- Wenn IDEA MCP laeuft, erscheinen zusaetzliche `idea__...` Tools.
- Ein Aufruf von `idea__get_all_open_file_paths` wird an IDEA weitergeleitet.
- Ein Aufruf von `idea__execute_terminal_command` wird an IDEA weitergeleitet.
- Wenn IDEA nicht laeuft, startet Pyloros trotzdem.

## Nicht in diesem Schritt

- Index MCP Server.
- Eigene Custom Tools ausser `pyloros__ping`.
- UI.
- Persistente Token Stores.
- Parallele JSON-RPC Calls zu IDEA.
