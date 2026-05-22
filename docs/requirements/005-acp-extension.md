Ja. Hier ist eine **fertige Architektur als Markdown-Dokument** für Pyloros mit `ToolCatalog`, `ProviderRegistry`, echten MCP-Upstreams und virtuellen ACP-Providern.

---

# Pyloros Architecture

## 1. Zielbild

**Pyloros ist ein Headless Agent Capability Gateway.**

Nach außen spricht Pyloros primär **MCP** und stellt einem Client wie ChatGPT, Copilot CLI, Claude, Goose oder einem eigenen Agenten eine flache Liste von Tools bereit.

Intern aggregiert Pyloros unterschiedliche Provider-Typen:

```text id="4mvd1x"
ChatGPT / Copilot / Claude / Goose / eigener Agent
        ↓ MCP
Pyloros MCP Server
        ↓
ToolCatalog
        ↓
ToolRouter
        ↓
ProviderRegistry
        ↓
 ┌─────────────────────────────┬─────────────────────────────┬─────────────────────────────┐
 │ Native Pyloros Tools        │ Real MCP Providers           │ Virtual ACP Providers        │
 │ get_status                  │ intellij/*                   │ copilot/*                    │
 │ list_providers              │ github/*                     │ goose/*                      │
 │ reload_catalog              │ filesystem/*                 │ claude/*                     │
 └─────────────────────────────┴─────────────────────────────┴─────────────────────────────┘
```

Pyloros existiert damit nicht als UI-Plugin, sondern als **Maschinen-API für KI-Agenten**.

Das passt zu deinem aktuellen Repository: Pyloros ist bereits als Vert.x-Gateway für MCP-Tool-Forwarding, OAuth, Tool-Prefix-Isolation und mehrere Upstream-Provider beschrieben. 

---

## 2. Grundprinzipien

### 2.1 Nach außen immer MCP

Der öffentliche Vertrag von Pyloros ist MCP:

```text id="6g80sf"
tools/list
tools/call
resources/list
resources/read
prompts/list
prompts/get
```

MCP-Tools werden über `tools/list` entdeckt und über `tools/call` per eindeutigem Namen aufgerufen. Ein Tool besitzt dabei unter anderem `name`, `description`, `inputSchema` und optional `outputSchema`. Außerdem sieht MCP `notifications/tools/list_changed` vor, wenn sich die Toolliste ändert. ([Model Context Protocol][1])

### 2.2 Intern beliebige Provider

Ein Tool muss intern nicht von einem echten MCP-Server kommen.

Ein Tool kann intern ausgeführt werden durch:

```text id="kujyx2"
- lokalen Java-Code
- echten MCP-Upstream
- ACP-Agent
- REST-Service
- Shell-Prozess
- Composite-/Agentennetzwerk
```

Für ChatGPT sieht trotzdem alles gleich aus:

```text id="cprap6"
get_status
intellij/get_problems
github/list_pull_requests
copilot/run_task
goose/start_task
```

### 2.3 ToolCatalog statt ToolRegistry

Pyloros braucht zwei getrennte Konzepte:

```text id="0oc1yf"
ProviderRegistry
  verwaltet Quellen: Ports, Prozesse, URLs, Auth, Laufzeitstatus

ToolCatalog
  veröffentlicht die finale flache MCP-Tool-Sicht
```

Der `ToolCatalog` ist das, was ChatGPT sieht.

Die `ProviderRegistry` ist das, was Pyloros intern braucht, um echte MCP-Server, ACP-Agenten und native Tools zu verwalten.

---

## 3. Architekturübersicht

```text id="swlku9"
com.aresstack.pyloros
├─ bootstrap
│  └─ PylorosApplication
│
├─ domain
│  ├─ provider
│  │  ├─ ProviderId
│  │  ├─ ProviderType
│  │  ├─ ProviderDescriptor
│  │  ├─ ProviderStatus
│  │  └─ ProviderRegistry
│  │
│  ├─ tool
│  │  ├─ ToolProvider
│  │  ├─ ToolCatalog
│  │  ├─ ToolRouter
│  │  ├─ ToolAddress
│  │  ├─ ToolDefinition
│  │  ├─ ExposedToolDefinition
│  │  ├─ ToolCallRequest
│  │  ├─ ToolCallResult
│  │  ├─ ToolNameResolver
│  │  ├─ ToolView
│  │  └─ ToolCollision
│  │
│  ├─ policy
│  │  ├─ ToolExecutionPolicy
│  │  ├─ PermissionDecision
│  │  ├─ PermissionRequest
│  │  └─ RiskLevel
│  │
│  └─ plugin
│     ├─ PylorosPlugin
│     └─ PluginContext
│
├─ application
│  ├─ ListToolsUseCase
│  ├─ CallToolUseCase
│  ├─ RebuildToolCatalogUseCase
│  ├─ ListProvidersUseCase
│  ├─ StartAgentTaskUseCase
│  ├─ ContinueAgentTaskUseCase
│  └─ DecidePermissionUseCase
│
├─ infrastructure
│  ├─ mcp
│  │  ├─ McpServerRoutes
│  │  ├─ McpMessageMapper
│  │  ├─ McpUpstreamToolProvider
│  │  └─ McpUpstreamClient
│  │
│  ├─ acp
│  │  ├─ AcpVirtualToolProvider
│  │  ├─ AcpAgentClient
│  │  ├─ AcpProcessLauncher
│  │  ├─ AcpSessionRepository
│  │  └─ AcpEventMapper
│  │
│  ├─ native
│  │  └─ NativePylorosToolProvider
│  │
│  ├─ plugin
│  │  ├─ ServiceLoaderPluginRegistry
│  │  └─ PluginClassLoaderFactory
│  │
│  ├─ config
│  │  ├─ PylorosConfigurationLoader
│  │  ├─ ProviderConfigurationMapper
│  │  └─ ToolNameStyle
│  │
│  └─ vertx
│     ├─ VertxHttpServer
│     └─ JsonRpcRouteHandler
```

---

## 4. Zentrale Bausteine

## 4.1 ToolProvider

`ToolProvider` ist der zentrale Port.

Er beschreibt nicht MCP, sondern allgemein eine Quelle ausführbarer Tools.

```java id="rz8zpm"
package com.aresstack.pyloros.domain.tool;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ToolProvider {

    ProviderId getProviderId();

    CompletionStage<List<ToolDefinition>> listTools(ToolView toolView);

    CompletionStage<ToolCallResult> callTool(ToolCallRequest request);
}
```

Implementierungen:

```text id="67c1p3"
NativePylorosToolProvider
McpUpstreamToolProvider
AcpVirtualToolProvider
CompositeToolProvider
RestToolProvider
```

Wichtig: Kein `AbstractToolProviderAdapter` im ersten Schritt. Prefixing, Views und Routing gehören nicht in eine Basisklasse, sondern in eigene Services.

---

## 4.2 ProviderRegistry

Die `ProviderRegistry` kennt Provider, nicht Tools.

Sie beantwortet:

```text id="qolpi5"
Welche Provider gibt es?
Wie erreiche ich sie?
Sind sie aktiv?
Welcher ToolProvider gehört zur ProviderId?
```

```java id="gdty9t"
package com.aresstack.pyloros.domain.provider;

import com.aresstack.pyloros.domain.tool.ToolProvider;

import java.util.List;

public interface ProviderRegistry {

    List<ProviderDescriptor> listProviderDescriptors();

    List<ToolProvider> listToolProviders();

    ToolProvider getToolProvider(ProviderId providerId);

    ProviderStatus getProviderStatus(ProviderId providerId);
}
```

Beispiele für Provider:

```text id="7jwcdi"
pyloros   native  prefix=""
intellij  mcp     prefix="intellij/"
github    mcp     prefix="github/"
copilot   acp     prefix="copilot/"
goose     acp     prefix="goose/"
```

---

## 4.3 ToolCatalog

Der `ToolCatalog` ist die flache Sicht für MCP-Clients.

Er beantwortet:

```text id="0z27r4"
Welche Tools sieht der Client?
Welcher finale Toolname gehört zu welchem Provider?
Ist der finale Toolname eindeutig?
```

```java id="53e2mb"
package com.aresstack.pyloros.domain.tool;

import java.util.List;

public interface ToolCatalog {

    List<ExposedToolDefinition> listTools(ToolView toolView);

    ToolAddress resolveTool(String exposedToolName);
}
```

Beispielinhalt:

```text id="ul6xdn"
get_status
list_providers
reload_catalog

intellij/get_problems
intellij/get_open_files
intellij/run_configuration

github/list_pull_requests
github/create_pull_request

copilot/run_task
copilot/start_task
copilot/get_task_status
copilot/cancel_task
```

---

## 4.4 ToolAddress

`ToolAddress` ist die interne Route.

```java id="ypomwg"
package com.aresstack.pyloros.domain.tool;

import com.aresstack.pyloros.domain.provider.ProviderId;

public final class ToolAddress {

    private final ProviderId providerId;
    private final String nativeToolName;

    public ToolAddress(ProviderId providerId, String nativeToolName) {
        this.providerId = providerId;
        this.nativeToolName = nativeToolName;
    }

    public ProviderId getProviderId() {
        return providerId;
    }

    public String getNativeToolName() {
        return nativeToolName;
    }
}
```

Beispiel:

```text id="wwv0zc"
exposedName = "intellij/problems"

ToolAddress:
  providerId = "intellij"
  nativeToolName = "get_problems"
```

---

## 4.5 ToolRouter

Der `ToolRouter` führt den Call aus.

```text id="uqvaz3"
MCP tools/call
  ↓
CallToolUseCase
  ↓
ToolCatalog.resolveTool(exposedName)
  ↓
ProviderRegistry.getToolProvider(providerId)
  ↓
ToolProvider.callTool(nativeRequest)
```

```java id="wqvd2h"
package com.aresstack.pyloros.domain.tool;

import com.aresstack.pyloros.domain.provider.ProviderRegistry;

import java.util.concurrent.CompletionStage;

public final class ToolRouter {

    private final ToolCatalog toolCatalog;
    private final ProviderRegistry providerRegistry;

    public ToolRouter(ToolCatalog toolCatalog, ProviderRegistry providerRegistry) {
        this.toolCatalog = toolCatalog;
        this.providerRegistry = providerRegistry;
    }

    public CompletionStage<ToolCallResult> callTool(ToolCallRequest request) {
        ToolAddress address = toolCatalog.resolveTool(request.getToolName());
        ToolProvider provider = providerRegistry.getToolProvider(address.getProviderId());

        ToolCallRequest routedRequest = request.withToolName(address.getNativeToolName());
        return provider.callTool(routedRequest);
    }
}
```

---

## 5. Tool-Naming

## 5.1 Namensregeln

```text id="jvegvz"
Root ohne Prefix:
  Pyloros-native Meta-Tools

Prefix:
  echte Provider
  virtuelle Provider
  externe Systeme
  Agenten
```

Beispiel:

```text id="fdcqe6"
get_status
list_providers
reload_catalog

intellij/get_problems
github/list_pull_requests
copilot/run_task
goose/run_task
```

## 5.2 Konfigurierbare Prefixe

Prefixe müssen konfigurierbar sein, weil Provider kollidieren können.

```json id="65rh34"
{
  "toolNameStyle": "slash",
  "providers": [
    {
      "id": "intellij",
      "type": "mcp",
      "prefix": "intellij/",
      "endpoint": "http://localhost:63342/api/mcp",
      "toolNameOverrides": {
        "get_problems": "problems",
        "get_all_open_file_paths": "open_files"
      },
      "exposeInViews": ["public", "agent"]
    },
    {
      "id": "copilot",
      "type": "acp",
      "prefix": "copilot/",
      "command": "copilot",
      "args": ["--acp", "--stdio"],
      "toolNameOverrides": {
        "run_task": "run"
      },
      "exposeInViews": ["public"],
      "agentToolView": "agent"
    }
  ],
  "nativeTools": {
    "prefix": "",
    "exposeInViews": ["public", "agent"]
  }
}
```

Final sichtbare Tools:

```text id="kxx00f"
get_status
list_providers
intellij/problems
intellij/open_files
copilot/run
```

## 5.3 ToolNameStyle

Nicht jeder Host ist gleich tolerant gegenüber `/` in Toolnamen. Deshalb sollte Pyloros das Rendering kapseln.

```text id="rgo2vm"
slash:
  intellij/get_problems

double_underscore:
  intellij__get_problems

flat:
  intellij_get_problems
```

Intern bleibt der kanonische Name unabhängig davon:

```text id="q1f2q1"
ToolAddress(providerId="intellij", nativeToolName="get_problems")
```

## 5.4 Kollisionsprüfung

Beim Catalog-Rebuild gilt:

```text id="9oi36a"
1. Alle Provider laden.
2. Alle nativen Tooldefinitionen abfragen.
3. Prefix + Override anwenden.
4. Finale Namen erzeugen.
5. Kollisionen erkennen.
6. Catalog nur veröffentlichen, wenn er gültig ist.
```

Fehlerbeispiel:

```text id="sbs3qp"
Tool name collision detected:

exposedName:
  intellij/problems

candidates:
  provider=intellij, nativeTool=get_problems
  provider=idea2, nativeTool=problems

Fix:
  change provider prefix or toolNameOverrides
```

Keine stillen Überschreibungen.

---

## 6. Tool Views

Pyloros braucht mehrere Sichten auf denselben Provider-Bestand.

```text id="qiqob0"
public
  Sicht für ChatGPT oder externe MCP-Clients

agent
  Sicht für interne ACP-Agenten

admin
  Sicht für Diagnose und Konfiguration

internal
  Sicht für Pyloros selbst
```

## 6.1 Public View

```text id="qc1qnt"
get_status
list_providers
intellij/*
github/*
copilot/*
goose/*
```

## 6.2 Agent View

```text id="q5h0s4"
get_status
intellij/*
github/*
filesystem/*
```

Nicht enthalten:

```text id="3qxn60"
copilot/*
goose/*
claude/*
```

Grund: Ein ACP-Agent darf nicht den virtuellen Provider sehen, der ihn selbst startet. Sonst entsteht Rekursion.

```text id="tkmmpy"
ChatGPT
  ↓
Pyloros copilot/run_task
  ↓
Copilot Agent
  ↓
Pyloros copilot/run_task
  ↓
Copilot Agent
  ↓
...
```

---

## 7. Provider-Typen

## 7.1 NativePylorosToolProvider

Für eigene Root-Tools.

Beispiele:

```text id="z84892"
get_status
list_providers
describe_provider
reload_catalog
validate_catalog
list_views
```

Diese Tools liegen ohne Prefix im Root.

```java id="dpr88x"
package com.aresstack.pyloros.infrastructure.nativeprovider;

import com.aresstack.pyloros.domain.tool.ToolCallRequest;
import com.aresstack.pyloros.domain.tool.ToolCallResult;
import com.aresstack.pyloros.domain.tool.ToolDefinition;
import com.aresstack.pyloros.domain.tool.ToolProvider;
import com.aresstack.pyloros.domain.tool.ToolView;
import com.aresstack.pyloros.domain.provider.ProviderId;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class NativePylorosToolProvider implements ToolProvider {

    private final ProviderId providerId;
    private final List<ToolDefinition> toolDefinitions;
    private final NativeToolDispatcher dispatcher;

    public NativePylorosToolProvider(
            ProviderId providerId,
            List<ToolDefinition> toolDefinitions,
            NativeToolDispatcher dispatcher) {
        this.providerId = providerId;
        this.toolDefinitions = toolDefinitions;
        this.dispatcher = dispatcher;
    }

    @Override
    public ProviderId getProviderId() {
        return providerId;
    }

    @Override
    public CompletionStage<List<ToolDefinition>> listTools(ToolView toolView) {
        return CompletableFuture.completedFuture(toolDefinitions);
    }

    @Override
    public CompletionStage<ToolCallResult> callTool(ToolCallRequest request) {
        return dispatcher.dispatch(request);
    }
}
```

---

## 7.2 McpUpstreamToolProvider

Für echte MCP-Server.

Beispiele:

```text id="t7qeri"
intellij/*
github/*
filesystem/*
postgres/*
browser/*
```

Der Provider macht intern:

```text id="9b20d6"
listTools()
  → upstream tools/list

callTool()
  → upstream tools/call
```

Pyloros mappt Toolnamen davor und danach.

```text id="c8h0vs"
public exposed:
  intellij/problems

upstream native:
  get_problems
```

---

## 7.3 AcpVirtualToolProvider

Für virtuelle MCP-Server, die intern ACP nutzen.

Beispiele:

```text id="mowqjc"
copilot/run_task
copilot/start_task
copilot/get_task_status
copilot/cancel_task
copilot/approve_permission
copilot/reject_permission
```

ACP ist hier kein öffentlicher Vertrag für ChatGPT. Es ist nur die interne Verbindung zum Agenten.

ACP-Sessions werden mit `session/new` erstellt. Dabei bekommt der Agent ein Arbeitsverzeichnis und eine Liste von MCP-Servern, die er verwenden darf. ([Agent Client Protocol][2]) Danach sendet der Client Prompts per `session/prompt`; der Agent streamt Pläne, Textausgaben und Tool-Call-Updates über `session/update`. ([Agent Client Protocol][3])

Ablauf:

```text id="mg17c5"
tools/call copilot/run_task
  ↓
AcpVirtualToolProvider
  ↓
AcpAgentClient.startSession(...)
  ↓
session/new mit cwd + mcpServers
  ↓
session/prompt
  ↓
session/update Events sammeln
  ↓
ToolCallResult zurückgeben
```

Synchrones Tool:

```text id="mvzux7"
copilot/run_task
```

Asynchrone Tools:

```text id="ghl5ej"
copilot/start_task
copilot/get_task_status
copilot/get_task_result
copilot/cancel_task
```

Für Agentenläufe ist asynchron meist sauberer.

---

## 7.4 CompositeToolProvider

Für Agentennetzwerke oder ganze Tool-Bündel.

Beispiele:

```text id="1ldif8"
devteam/review_changes
devteam/fix_failing_test
devteam/prepare_pull_request
```

Intern kann ein Composite Provider mehrere Provider orchestrieren:

```text id="mmjsly"
devteam/fix_failing_test
  ↓
copilot/start_task
  ↓
intellij/get_problems
  ↓
github/list_pull_requests
  ↓
gradle/run_test
```

Wichtig: Das Composite bleibt nach außen ein normaler `ToolProvider`.

---

## 8. MCP Facade

Die Vert.x-Routes bleiben dünn.

```text id="17jei9"
McpServerRoutes
  - JSON-RPC annehmen
  - Auth prüfen
  - Request validieren
  - UseCase aufrufen
  - MCP Response erzeugen
```

## 8.1 tools/list

```text id="1l2yht"
Client → tools/list
  ↓
ListToolsUseCase
  ↓
ToolCatalog.listTools(publicView)
  ↓
MCP tools/list response
```

## 8.2 tools/call

```text id="wgytxd"
Client → tools/call name="intellij/problems"
  ↓
CallToolUseCase
  ↓
ToolRouter
  ↓
McpUpstreamToolProvider
  ↓
IntelliJ MCP Server
```

## 8.3 list_changed

Wenn Provider neu geladen, deaktiviert oder umkonfiguriert werden:

```text id="s885cc"
RebuildToolCatalogUseCase
  ↓
ToolCatalogSnapshot ersetzen
  ↓
notifications/tools/list_changed senden
```

MCP sieht diese Benachrichtigung vor, wenn der Server die `tools.listChanged`-Capability deklariert. ([Model Context Protocol][1])

---

## 9. Resources

Tools sind für Aktionen. Resources sind für Kontext und Metadaten.

MCP-Resources sind über URIs identifizierbare Kontextdaten und werden über `resources/list` und `resources/read` bereitgestellt. Sie eignen sich für Dateien, Schemata, Provider-Metadaten oder anwendungsspezifische Informationen. ([Model Context Protocol][4])

Pyloros-Resources:

```text id="8lgjeh"
pyloros://providers
pyloros://providers/intellij
pyloros://providers/copilot

pyloros://catalog/public
pyloros://catalog/agent
pyloros://catalog/admin

pyloros://tools/intellij/problems
pyloros://tools/copilot/run_task

pyloros://sessions
pyloros://sessions/{sessionId}
pyloros://tasks/{taskId}
```

Beispiel `pyloros://providers`:

```json id="clpj1z"
{
  "providers": [
    {
      "id": "intellij",
      "type": "mcp",
      "prefix": "intellij/",
      "status": "available"
    },
    {
      "id": "copilot",
      "type": "acp",
      "prefix": "copilot/",
      "status": "available"
    }
  ]
}
```

---

## 10. Prompts

Prompts sind wiederverwendbare Workflows.

Beispiele:

```text id="g4f6lj"
review_current_changes
fix_current_test_failure
explain_current_stacktrace
prepare_pull_request
analyze_architecture_boundary
```

MCP-Prompts werden über `prompts/list` entdeckt und über `prompts/get` abgerufen; sie sind damit besser für wiederverwendbare Aufgabenanweisungen geeignet als künstliche Tools. ([Model Context Protocol][5])

Beispiel:

```json id="jihrb9"
{
  "name": "fix_current_test_failure",
  "description": "Diagnose and fix the currently failing Java test using IDE diagnostics and targeted test execution.",
  "arguments": [
    {
      "name": "moduleName",
      "description": "Optional module name",
      "required": false
    }
  ]
}
```

---

## 11. ACP als virtueller Provider

## 11.1 Public Tools

Der ACP-Provider bietet nach außen MCP-Tools an:

```text id="p24f1w"
copilot/run_task
copilot/start_task
copilot/get_task_status
copilot/get_task_result
copilot/cancel_task
copilot/approve_permission
copilot/reject_permission
```

## 11.2 Internes ACP-Mapping

```text id="ui16s3"
copilot/run_task
  → session/new
  → session/prompt
  → wait until stopReason
  → aggregate events
  → return ToolCallResult
```

```text id="qg1kex"
copilot/start_task
  → session/new
  → session/prompt async
  → return taskId
```

```text id="lznt5n"
copilot/get_task_status
  → read AcpSessionRepository
  → return latest events, pending permissions, current state
```

## 11.3 Agent Tool View

Der ACP-Agent bekommt nicht die Public View.

Er bekommt eine eigene MCP-Sicht:

```text id="7hoknh"
agent view:
  get_status
  intellij/*
  github/*
  filesystem/*
  app/*
```

Nicht:

```text id="65bko2"
copilot/*
goose/*
claude/*
```

## 11.4 Session Management

```text id="5mka1c"
AgentTask
├─ taskId
├─ providerId
├─ acpSessionId
├─ state
├─ cwd
├─ prompt
├─ startedAt
├─ updatedAt
├─ events
├─ pendingPermissions
└─ result
```

States:

```text id="3eh4o5"
created
running
waiting_for_permission
completed
failed
cancelled
timeout
```

---

## 12. Permission Policy

MCP warnt explizit vor Sicherheitsrisiken: Server müssen Eingaben validieren, Zugriffskontrollen umsetzen, rate-limiten und Ausgaben bereinigen; Clients sollten sensible Tool-Aufrufe bestätigen lassen, Tool-Eingaben anzeigen, Ergebnisse validieren, Timeouts setzen und Tool-Nutzung auditieren. ([Model Context Protocol][1])

Pyloros sollte deshalb eine Policy-Schicht haben.

```text id="b0o65j"
read/search/status
  → allow

write/edit/delete
  → require approval

execute/shell
  → require approval

network/external-url
  → allowlist or approval

recursive-agent-call
  → deny

unknown
  → deny
```

```java id="y82rkp"
package com.aresstack.pyloros.domain.policy;

import com.aresstack.pyloros.domain.tool.ToolCallRequest;

public interface ToolExecutionPolicy {

    PermissionDecision decide(ToolCallRequest request);
}
```

Beispielentscheidung:

```json id="ed6hxa"
{
  "decision": "requires_approval",
  "reason": "The tool wants to run a shell command.",
  "riskLevel": "high"
}
```

---

## 13. Plugin-System

## 13.1 Ziel

Plugins sollen Provider hinzufügen können.

Ein Plugin kann liefern:

```text id="cupd9w"
- einen MCP-Upstream Provider
- einen ACP-Agent Provider
- native Tools
- Composite Provider
- Prompts
- Resources
- Policies
```

Java 8 bietet mit `ServiceLoader` einen Standardmechanismus, um Service-Provider-Implementierungen über Interfaces zu laden; Provider werden typischerweise über `META-INF/services/<interface-name>` registriert. ([Oracle Docs][6])

## 13.2 Plugin Interface

```java id="77fzo5"
package com.aresstack.pyloros.domain.plugin;

import com.aresstack.pyloros.domain.tool.ToolProvider;

import java.util.List;

public interface PylorosPlugin {

    String getPluginId();

    List<ToolProvider> createToolProviders(PluginContext context);
}
```

## 13.3 PluginContext

```java id="bbrxqq"
package com.aresstack.pyloros.domain.plugin;

public interface PluginContext {

    McpClientFactory getMcpClientFactory();

    AcpClientFactory getAcpClientFactory();

    ConfigurationView getConfigurationView();

    PolicyRegistry getPolicyRegistry();
}
```

## 13.4 ServiceLoader Registry

```java id="brcf2k"
package com.aresstack.pyloros.infrastructure.plugin;

import com.aresstack.pyloros.domain.plugin.PluginContext;
import com.aresstack.pyloros.domain.plugin.PylorosPlugin;
import com.aresstack.pyloros.domain.tool.ToolProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class ServiceLoaderPluginRegistry {

    public List<ToolProvider> loadToolProviders(PluginContext context) {
        List<ToolProvider> providers = new ArrayList<ToolProvider>();
        ServiceLoader<PylorosPlugin> plugins = ServiceLoader.load(PylorosPlugin.class);

        for (PylorosPlugin plugin : plugins) {
            providers.addAll(plugin.createToolProviders(context));
        }

        return providers;
    }
}
```

Plugin-JAR:

```text id="bo4q1p"
META-INF/services/com.aresstack.pyloros.domain.plugin.PylorosPlugin
```

Inhalt:

```text id="m9j3nh"
com.example.pyloros.intellij.IntelliJProviderPlugin
```

---

## 14. Configuration

## 14.1 Beispiel

```json id="gf2imq"
{
  "server": {
    "port": 64343,
    "publicOrigin": "https://example.com"
  },
  "toolCatalog": {
    "toolNameStyle": "slash",
    "failOnCollision": true,
    "publishListChangedNotifications": true
  },
  "views": {
    "public": {
      "includeProviders": ["pyloros", "intellij", "github", "copilot"]
    },
    "agent": {
      "includeProviders": ["pyloros", "intellij", "github"],
      "excludeProviders": ["copilot", "goose", "claude"]
    }
  },
  "providers": [
    {
      "id": "pyloros",
      "type": "native",
      "prefix": "",
      "exposeInViews": ["public", "agent", "admin"]
    },
    {
      "id": "intellij",
      "type": "mcp",
      "prefix": "intellij/",
      "transport": {
        "kind": "http",
        "url": "http://localhost:63342/api/mcp"
      },
      "toolNameOverrides": {
        "get_all_open_file_paths": "open_files",
        "get_problems": "problems"
      },
      "exposeInViews": ["public", "agent"]
    },
    {
      "id": "copilot",
      "type": "acp",
      "prefix": "copilot/",
      "process": {
        "command": "copilot",
        "args": ["--acp", "--stdio"],
        "workingDirectory": "C:/Projects"
      },
      "agentToolView": "agent",
      "exposeInViews": ["public"],
      "tools": {
        "run_task": {
          "timeoutSeconds": 300
        },
        "start_task": {
          "timeoutSeconds": 30
        }
      }
    }
  ],
  "policy": {
    "defaultDecision": "deny",
    "rules": [
      {
        "match": {
          "riskLevel": "read"
        },
        "decision": "allow"
      },
      {
        "match": {
          "riskLevel": "write"
        },
        "decision": "requires_approval"
      },
      {
        "match": {
          "riskLevel": "execute"
        },
        "decision": "requires_approval"
      }
    ]
  }
}
```

---

## 15. Startup Lifecycle

```text id="6cnkln"
1. Load configuration
2. Load plugins
3. Build ProviderRegistry
4. Probe provider availability
5. Discover provider tools
6. Build ToolCatalog
7. Validate collisions
8. Start MCP HTTP server
9. Expose tools/resources/prompts
10. Watch provider changes
```

Wenn ein Provider später dazukommt oder verschwindet:

```text id="5k30zb"
1. Mark provider dirty
2. Re-discover tools
3. Rebuild ToolCatalog snapshot
4. Validate collisions
5. Swap catalog atomically
6. Emit notifications/tools/list_changed
```

---

## 16. Runtime Flows

## 16.1 ChatGPT ruft IntelliJ-Tool auf

```text id="1oaifk"
ChatGPT
  ↓ tools/call intellij/problems
Pyloros McpServerRoutes
  ↓
CallToolUseCase
  ↓
ToolRouter
  ↓
ToolCatalog.resolveTool("intellij/problems")
  ↓
ToolAddress("intellij", "get_problems")
  ↓
ProviderRegistry.getToolProvider("intellij")
  ↓
McpUpstreamToolProvider.callTool("get_problems")
  ↓
IntelliJ MCP Server
```

## 16.2 ChatGPT ruft Copilot-Agent auf

```text id="we0tzj"
ChatGPT
  ↓ tools/call copilot/run_task
Pyloros
  ↓
AcpVirtualToolProvider
  ↓
copilot --acp --stdio
  ↓ session/new with agent tool view
  ↓ session/prompt
  ↓ session/update events
  ↓ stopReason=end_turn
Pyloros
  ↓ ToolCallResult
ChatGPT
```

## 16.3 Copilot-Agent ruft interne Pyloros-Tools auf

```text id="4xn93n"
Copilot Agent
  ↓ MCP tools/list
Pyloros /mcp/agent-view
  ↓
intellij/problems
github/list_pull_requests
get_status
```

Nicht sichtbar:

```text id="o3cscs"
copilot/run_task
```

---

## 17. Error Handling

## 17.1 Fehlerarten

```text id="914zle"
Protocol Error
  JSON-RPC ungültig, unbekanntes Tool, falsche Parameter

Tool Execution Error
  Provider erreichbar, Tool schlägt fachlich fehl

Provider Error
  Upstream nicht erreichbar, Prozess beendet, Timeout

Policy Error
  Toolaufruf verboten oder Freigabe fehlt

Catalog Error
  Kollision, ungültige Konfiguration, Schemafehler
```

## 17.2 MCP-Mapping

```text id="ddwhzg"
unknown tool
  → JSON-RPC error -32602

provider unavailable
  → ToolResult isError=true

permission required
  → ToolResult isError=false with structuredContent.state="permission_required"

policy denied
  → ToolResult isError=true
```

Beispiel:

```json id="zwwdv6"
{
  "content": [
    {
      "type": "text",
      "text": "Permission required before running shell command."
    }
  ],
  "structuredContent": {
    "state": "permission_required",
    "permissionId": "perm-123",
    "title": "Run Gradle tests",
    "command": "./gradlew test"
  },
  "isError": false
}
```

---

## 18. Auditing

Jeder Toolaufruf sollte auditierbar sein.

```text id="c7uozx"
timestamp
clientId
sessionId
toolName
providerId
nativeToolName
argumentsHash
riskLevel
decision
durationMs
resultState
```

Keine Secrets im Log.

Argumente nur vollständig loggen, wenn der Tooltyp als unkritisch markiert ist.

---

## 19. Security Boundaries

## 19.1 Root Tools

Root-Tools dürfen keine gefährlichen Operationen ausführen, außer sie sind klar als Admin-Tools markiert.

```text id="tov9gk"
get_status           safe
list_providers       safe
reload_catalog       admin
shutdown_provider    admin
```

## 19.2 Provider Isolation

Jeder Provider bekommt:

```text id="51j41k"
providerId
prefix
allowedViews
policyProfile
timeoutProfile
authProfile
```

## 19.3 Agent Isolation

Virtuelle ACP-Provider bekommen eine eingeschränkte Tool View.

```text id="d9sbff"
copilot provider:
  public visible: yes
  agent visible to itself: no
  agentToolView: agent
```

---

## 20. Empfohlene erste Implementierungsstufe

## Milestone 1: ToolCatalog Core

```text id="o1pcva"
- ToolProvider Interface
- ProviderRegistry
- ToolCatalog
- ToolNameResolver
- ToolRouter
- ToolView
- Collision Validation
```

## Milestone 2: Bestehendes MCP-Forwarding umbauen

```text id="gpm3hf"
- IdeaToolProvider → McpUpstreamToolProvider
- Prefix/Override-Konfiguration
- /tools/list aus ToolCatalog erzeugen
- /tools/call über ToolRouter ausführen
```

## Milestone 3: Native Root Tools

```text id="9zmhcd"
get_status
list_providers
describe_provider
validate_catalog
reload_catalog
list_views
```

## Milestone 4: ACP Virtual Provider

```text id="68flb1"
copilot/run_task
copilot/start_task
copilot/get_task_status
copilot/cancel_task
permission flow
agent tool view
```

## Milestone 5: Plugin-System

```text id="wv63xk"
PylorosPlugin
PluginContext
ServiceLoaderPluginRegistry
plugin config
provider contribution
```

## Milestone 6: Resources und Prompts

```text id="bf0gxh"
resources/list
resources/read
prompts/list
prompts/get
provider metadata
tool catalog resources
workflow prompts
```

---

## 21. Wichtigste Designentscheidungen

| Entscheidung                                  | Begründung                                                                                    |
| --------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `ToolProvider` als Interface                  | Provider bleiben austauschbar und unabhängig von MCP/ACP.                                     |
| `ToolCatalog` statt großer `ToolRegistry`     | Der Catalog ist die flache MCP-Sicht; Provider-Verwaltung bleibt getrennt.                    |
| `ProviderRegistry` separat                    | Ports, Prozesse, URLs, Auth und Status gehören zu Providern, nicht zu Tools.                  |
| Keine abstrakte Basisklasse am Anfang         | Komposition vermeidet frühe Vererbungskopplung.                                               |
| Prefix/Override zentral im `ToolNameResolver` | Provider müssen keine Exportnamen kennen.                                                     |
| Tool Views                                    | Verhindert Rekursion und ermöglicht unterschiedliche Sichten für ChatGPT, Agenten und Admins. |
| ACP als virtueller Provider                   | Für ChatGPT bleibt alles MCP; intern kann Pyloros Agenten steuern.                            |
| Root nur für Pyloros-native Tools             | Externe Systeme und Agenten bekommen immer einen Namespace.                                   |
| Plugins über `ServiceLoader`                  | Java-8-kompatibel, einfach, später erweiterbar.                                               |

---

## 22. Kurzform der finalen Architektur

```text id="69ib1v"
McpServerRoutes
  ↓
UseCases
  ↓
ToolCatalog
  ↓
ToolRouter
  ↓
ProviderRegistry
  ↓
ToolProvider
 ├─ NativePylorosToolProvider
 ├─ McpUpstreamToolProvider
 ├─ AcpVirtualToolProvider
 └─ CompositeToolProvider
```

**Pyloros veröffentlicht einen flachen MCP-ToolCatalog.
Intern verwaltet es Provider.
Echte MCP-Server und virtuelle ACP-Agenten sind gleichrangige ToolProvider.
Prefixe, Overrides und Views verhindern Kollisionen und Rekursion.**

[1]: https://modelcontextprotocol.io/specification/2025-06-18/server/tools "Tools - Model Context Protocol"
[2]: https://agentclientprotocol.com/protocol/session-setup "Session Setup - Agent Client Protocol"
[3]: https://agentclientprotocol.com/protocol/prompt-turn "Prompt Turn - Agent Client Protocol"
[4]: https://modelcontextprotocol.io/specification/2025-06-18/server/resources "Resources - Model Context Protocol"
[5]: https://modelcontextprotocol.io/specification/2025-06-18/server/prompts "Prompts - Model Context Protocol"
[6]: https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html "ServiceLoader (Java Platform SE 8 )"
