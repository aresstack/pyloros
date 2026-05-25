# R6-04 Spike: Java ACP SDK und Java MCP SDK fuer Manager Agent

Parent issue: aresstack/pyloros#59

Related: #64

## Ziel

Entscheidungsvorlage fuer den Java-21-Manager-Agenten:

- welches Java ACP SDK fuer `session/new`, `session/prompt`, `session/update`
- welches Java MCP SDK fuer `tools/list`, `tools/call`
- welche Versionen fuer R6 (ohne Snapshot-Zwang)
- welche Risiken fuer Java 21, Packaging und Distribution

Stand: 2026-05-25

## Ergebnis (Kurzfassung)

### ACP SDK (R6-Empfehlung)

- **SDK:** `com.agentclientprotocol` Java SDK
- **Dependency (minimal):** `com.agentclientprotocol:acp-core:0.11.0`
- **Optional:** `com.agentclientprotocol:acp-agent-support:0.11.0` (Annotation-Bootstrap), `com.agentclientprotocol:acp-websocket-jetty:0.11.0` (nur falls WS-Transport benoetigt)

**Warum:**

- Stable Release `0.11.0` auf Maven Central verfuegbar (kein Snapshot noetig).
- Java 17+ kompatibel, damit Java 21 kompatibel.
- Entspricht exakt dem benoetigten ACP-Lifecycle fuer den Manager-Agenten.

### MCP SDK (R6-Empfehlung)

- **SDK:** `io.modelcontextprotocol.sdk` Java SDK
- **Dependency (R6-stabil):** `io.modelcontextprotocol.sdk:mcp:1.1.3`
- **Alternative fuer feinere Kontrolle:** `io.modelcontextprotocol.sdk:mcp-core:1.1.3` + explizites JSON-Modul

**Warum:**

- 1.1.3 ist die aktuelle stabile GA-Linie.
- 2.0.0-Mx ist verfuegbar, aber **Milestone** und damit fuer R6 nicht Default.
- SDK liefert den benoetigten MCP-Client-Flow (`initialize`, `listTools`, `callTool`) inkl. HTTP/SSE/Streamable HTTP Transport.

## Nicht-Empfehlung fuer R6

- Kein Einsatz von Snapshot-Versionen (`*-SNAPSHOT`) ohne explizite Freigabe.
- Kein Default auf MCP `2.0.0-M3` fuer R6-Core-Entscheidung (Milestone-Risiko).
- Keine Kopplung an Spring-Starter im Pyloros-Core (Projektregel: Vert.x, kein Spring im Core).

## Minimaler Bootstrap-Sketch

**Wichtig:** Die folgenden Snippets sind **API sketch, not compile-verified in this repository**.
Sie dienen nur als Integrationsskizze fuer den separaten Manager-Agent-Deliverable.

## 1) ACP Agent Session (Java ACP SDK)

```java
var transport = new StdioAcpAgentTransport();
var agent = AcpAgent.sync(transport)
    .initializeHandler(req -> InitializeResponse.ok())
    .newSessionHandler(req -> new NewSessionResponse(UUID.randomUUID().toString(), null, null))
    .promptHandler((req, ctx) -> {
        ctx.sendMessage("manager-agent alive");
        return PromptResponse.endTurn();
    })
    .build();

agent.run();
```

## 2) MCP Client gegen Pyloros-Endpunkt

```java
var transport = HttpClientStreamableHttpTransport.builder("http://localhost:8080")
    .endpoint("/pyloros")
    .build();

var mcpClient = McpClient.sync(transport).build();
mcpClient.initialize();
var tools = mcpClient.listTools();
var result = mcpClient.callTool(new CallToolRequest("get_status", Map.of()));
```

## Compile-/Smoke-Nachweis und Blockade-Bewertung

- **Nachweis in diesem Spike:** Dependency- und Release-Validierung ueber Maven-Metadata + Upstream-SDK-Releases.
- **Begruendete Blockade fuer In-Repo Compile-Smoke des Manager-Agent-Binaries:** In diesem Repository ist der Manager-Agent explizit als separater Deliverable vorgesehen (`docs/requirements/006-acp-manager-agent.md`), nicht als vollwertiges Runtime-Modul im Core.
- **R6-Entscheidung:** SDK-Auswahl ist technisch freigegeben; ein separater Manager-Agent-Prototyp kann im naechsten Schritt als eigenes Modul/Repo mit minimalem `compileJava`-Smoke aufgebaut werden.

## Risiken (R6)

1. **ACP SDK ist 0.x (0.11.0):**
   - API-Aenderungen vor 1.0 moeglich.
   - Mitigation: Version pinnen, Wrapper an Manager-Agent-Grenze halten.

2. **MCP 2.0 nur Milestone (M1-M3):**
   - Hoeheres Migrationsrisiko waehrend R6.
   - Mitigation: fuer R6 auf 1.1.3 pinnen; 2.0 in separatem Upgrade-Track evaluieren.

3. **JSON/Dependency-Footprint:**
   - MCP Convenience-Modul `mcp` nutzt Jackson-Integration; bei zukuenftiger In-Prozess-Nutzung auf Version-Ausrichtung achten.
   - Mitigation: falls noetig `mcp-core` + explizites JSON-Modul waehlen.

4. **Packaging/Distribution:**
   - Manager-Agent als separater Prozess/JAR bringt eigene Dependency-Flaeche (ACP+MCP+ggf. Reactor).
   - Mitigation: separater Build/Release-Artifact, klare Version-Pins, kein Einzug in Pyloros-Core.

## Offene Fragen

1. Soll der erste Manager-Agent-Prototyp als neues Gradle-Submodul im Monorepo entstehen oder als separates Repository?
2. Soll fuer R6 bereits ein MCP-2.0-Milestone-Track parallel vorbereitet werden (nur experimentell)?
3. Standard-Transport fuer den Manager-Agenten gegen Pyloros MCP: Streamable HTTP oder SSE?
4. Brauchen wir fuer den ersten Prototypen bereits WS-Transport im ACP-Teil oder reicht STDIO?

## Verwendete Quellen

- ACP Java SDK Releases/README: `https://github.com/agentclientprotocol/java-sdk`
- ACP Maven Metadata: `https://repo1.maven.org/maven2/com/agentclientprotocol/acp-core/maven-metadata.xml`
- MCP Java SDK Releases/README: `https://github.com/modelcontextprotocol/java-sdk`
- MCP Maven Metadata: `https://repo1.maven.org/maven2/io/modelcontextprotocol/sdk/mcp/maven-metadata.xml`
