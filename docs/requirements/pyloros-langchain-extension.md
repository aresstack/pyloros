---

## 23. LangChain als virtueller Natural-Language Provider

## 23.1 Ziel

Pyloros soll neben echten MCP-Upstreams und virtuellen ACP-Providern einen dritten virtuellen Provider-Typ unterstützen:

```text
LangChainVirtualToolProvider
```

Dieser Provider ermöglicht natürliche Sprache als Eingabe. Der aufrufende MCP-Client muss dann nicht mehr selbst konkrete Tools auswählen und `tools/call` mit exakten Toolnamen ausführen.

Stattdessen ruft der Client ein Pyloros-AI-Tool auf, zum Beispiel:

```text
pyloros-ai/ask
```

Die eigentliche Toolauswahl übernimmt intern ein lokales LLM über LangChain4j. Das Modell soll bevorzugt über Ollama lokal auf der Maschine laufen.

Zielbild:

```text
Client
  ↓ MCP tools/call pyloros-ai/ask
Pyloros
  ↓
LangChainVirtualToolProvider
  ↓
LangChain4j + Ollama
  ↓
ToolCatalog.listTools(llm-agent view)
  ↓
ToolRouter.callTool(...)
  ↓
aggregierte MCP-Upstreams / native Tools
  ↓
natürliche Antwort
```

Pyloros bleibt dabei weiterhin das zentrale Capability Gateway. LangChain ist nur die Natural-Language-Orchestrierungsschicht.

---

## 23.2 Abgrenzung

Der LangChain-Provider ersetzt nicht:

```text
- MCP Aggregation
- ToolCatalog
- ToolRouter
- ProviderRegistry
- ACP Virtual Provider
- Permission Policy
- Auditing
```

Er ergänzt Pyloros um eine neue Zugriffsschicht:

```text
konkreter Toolcall:
  client -> intellij/get_problems

natürliche Sprache:
  client -> pyloros-ai/ask("Welche Probleme gibt es im aktuellen Projekt?")
```

Der LangChain-Provider darf keine eigenen Upstream-Tools direkt verwalten. Er muss die bestehenden Pyloros-Strukturen verwenden.

Richtig:

```text
LangChainVirtualToolProvider
  -> ToolCatalog
  -> ToolRouter
  -> ProviderRegistry
```

Nicht richtig:

```text
LangChainVirtualToolProvider
  -> eigene MCP-Upstream-Clients
  -> eigene Providerverwaltung
  -> eigener Toolkatalog
```

---

## 23.3 Provider-Typ

Die Provider-Typen werden erweitert:

```text
native
mcp
acp
langchain
composite
rest
```

Der neue Provider ist ein normaler `ToolProvider`.

```text
ToolProvider
 ├─ NativePylorosToolProvider
 ├─ McpUpstreamToolProvider
 ├─ AcpVirtualToolProvider
 ├─ LangChainVirtualToolProvider
 ├─ CompositeToolProvider
 └─ RestToolProvider
```

---

## 23.4 Öffentliche Tools des LangChain-Providers

Der LangChain-Provider veröffentlicht nur wenige, bewusst grobe Tools.

Erste Stufe:

```text
pyloros-ai/ask
```

Spätere optionale Tools:

```text
pyloros-ai/plan
pyloros-ai/start
pyloros-ai/get_status
pyloros-ai/get_result
pyloros-ai/cancel
```

### 23.4.1 pyloros-ai/ask

Synchroner Natural-Language-Aufruf.

Eingabe:

```json
{
  "prompt": "string",
  "context": "optional string",
  "maxToolCalls": "optional integer",
  "dryRun": "optional boolean"
}
```

Ausgabe:

```json
{
  "answer": "string",
  "usedTools": [
    {
      "toolName": "intellij/get_problems",
      "arguments": {},
      "durationMs": 123,
      "resultState": "success"
    }
  ],
  "stoppedReason": "completed | max_tool_calls | policy_denied | timeout | model_error"
}
```

### 23.4.2 pyloros-ai/plan

Erzeugt nur einen Plan und führt keine Tools aus.

Eingabe:

```json
{
  "prompt": "string",
  "context": "optional string"
}
```

Ausgabe:

```json
{
  "steps": [
    "List project modules",
    "Inspect current diagnostics",
    "Summarize findings"
  ],
  "candidateTools": [
    "intellij/get_project_modules",
    "intellij/get_problems"
  ],
  "willExecuteTools": false
}
```

### 23.4.3 pyloros-ai/start

Asynchroner Natural-Language-Aufruf für längere Agentenläufe.

Eingabe:

```json
{
  "prompt": "string",
  "context": "optional string",
  "maxToolCalls": "optional integer"
}
```

Ausgabe:

```json
{
  "taskId": "ai-task-123",
  "state": "running"
}
```

---

## 23.5 Eigene Tool View für das LLM

Der LangChain-Provider darf nicht die Public View verwenden.

Grund: Die Public View enthält den LangChain-Provider selbst. Würde das Modell `pyloros-ai/ask` als Tool sehen, könnte es rekursiv sich selbst aufrufen.

Neue View:

```text
llm-agent
```

Beispiel:

```json
{
  "views": {
    "public": {
      "includeProviders": [
        "pyloros",
        "intellij",
        "github",
        "intellij-index",
        "copilot",
        "pyloros-ai"
      ]
    },
    "llm-agent": {
      "includeProviders": [
        "pyloros",
        "intellij",
        "github",
        "intellij-index"
      ],
      "excludeProviders": [
        "pyloros-ai",
        "copilot",
        "goose",
        "claude"
      ]
    }
  }
}
```

Regel:

```text
Ein virtueller Agent-Provider darf sich selbst niemals in seiner internen Tool View sehen.
```

Das gilt für:

```text
- ACP Virtual Provider
- LangChain Virtual Provider
- spätere Composite-/Agenten-Provider
```

---

## 23.6 Interne Architektur

Neue Infrastrukturpakete:

```text
com.aresstack.pyloros.infrastructure.langchain
├─ LangChainVirtualToolProvider
├─ LangChainAgentService
├─ OllamaModelFactory
├─ LangChainToolCatalogAdapter
├─ PylorosToolSpecificationMapper
├─ PylorosToolExecutor
├─ ToolSelectionStrategy
├─ KeywordToolSelectionStrategy
├─ ToolExecutionLoop
├─ LangChainSessionRepository
└─ LangChainProviderConfiguration
```

### 23.6.1 LangChainVirtualToolProvider

Aufgabe:

```text
- veröffentlicht pyloros-ai/* Tools
- nimmt ToolCallRequest entgegen
- validiert Eingabe
- delegiert natürliche Anfrage an LangChainAgentService
- gibt ToolCallResult zurück
```

Skizze:

```java
package com.aresstack.pyloros.infrastructure.langchain;

import com.aresstack.pyloros.domain.provider.ProviderId;
import com.aresstack.pyloros.domain.tool.ToolCallRequest;
import com.aresstack.pyloros.domain.tool.ToolCallResult;
import com.aresstack.pyloros.domain.tool.ToolDefinition;
import com.aresstack.pyloros.domain.tool.ToolProvider;
import com.aresstack.pyloros.domain.tool.ToolView;

import java.util.List;
import java.util.concurrent.CompletionStage;

public final class LangChainVirtualToolProvider implements ToolProvider {

    private final ProviderId providerId;
    private final List<ToolDefinition> toolDefinitions;
    private final LangChainAgentService agentService;

    public LangChainVirtualToolProvider(
            ProviderId providerId,
            List<ToolDefinition> toolDefinitions,
            LangChainAgentService agentService) {
        this.providerId = providerId;
        this.toolDefinitions = toolDefinitions;
        this.agentService = agentService;
    }

    @Override
    public ProviderId getProviderId() {
        return providerId;
    }

    @Override
    public CompletionStage<List<ToolDefinition>> listTools(ToolView toolView) {
        return agentService.completedTools(toolDefinitions);
    }

    @Override
    public CompletionStage<ToolCallResult> callTool(ToolCallRequest request) {
        return agentService.answer(request);
    }
}
```

### 23.6.2 LangChainAgentService

Aufgabe:

```text
- baut pro Anfrage eine erlaubte Tool-Sicht
- wählt relevante Tools aus
- ruft lokales Ollama-Modell über LangChain4j auf
- verarbeitet Tool-Execution-Requests des Modells
- führt Tools über PylorosToolExecutor aus
- begrenzt Toolloops
- erzeugt finale Antwort
```

Ablauf:

```text
answer(request)
  ↓
extract prompt
  ↓
load llm-agent ToolView
  ↓
select relevant tools
  ↓
map Pyloros tools to LangChain tool specifications
  ↓
send prompt to Ollama model
  ↓
handle tool execution requests
  ↓
call PylorosToolExecutor
  ↓
send tool results back to model
  ↓
return final ToolCallResult
```

### 23.6.3 PylorosToolExecutor

Aufgabe:

```text
- nimmt Toolaufrufe des Modells entgegen
- prüft Policy
- routet den Aufruf über ToolRouter
- kürzt große Ergebnisse
- protokolliert Audit-Daten
- gibt Ergebnis an LangChainAgentService zurück
```

Wichtig:

```text
Der Executor darf keine Provider direkt aufrufen.
Er muss immer über ToolRouter gehen.
```

### 23.6.4 PylorosToolSpecificationMapper

Aufgabe:

```text
- wandelt ExposedToolDefinition in LangChain4j ToolSpecification um
- übernimmt Name, Beschreibung und Input-Schema
- kürzt zu lange Toolbeschreibungen kontrolliert
- entfernt interne Pyloros-Metadaten
```

---

## 23.7 Toolauswahl

Das Modell soll konzeptionell Zugriff auf den aggregierten Pyloros-Toolbestand bekommen. Praktisch sollen aber nicht immer alle Tools in jeden Modellaufruf gegeben werden.

Grund:

```text
- zu viele Tooldefinitionen verbrauchen Kontext
- zu viele Tools verschlechtern die Toolauswahl
- unnötig breite Toolauswahl erhöht Risiko falscher Aufrufe
```

Deshalb wird eine Strategie eingeführt:

```java
package com.aresstack.pyloros.infrastructure.langchain;

import com.aresstack.pyloros.domain.tool.ExposedToolDefinition;

import java.util.List;

public interface ToolSelectionStrategy {

    List<ExposedToolDefinition> selectTools(
            String prompt,
            List<ExposedToolDefinition> availableTools,
            int maxTools);
}
```

Erste Implementierung:

```text
KeywordToolSelectionStrategy
```

Heuristik:

```text
- Toolname matcht Prompt
- Providername matcht Prompt
- Beschreibung matcht Prompt
- native Pyloros-Status-/Listen-Tools immer optional erlauben
```

Spätere mögliche Implementierung:

```text
EmbeddingToolSelectionStrategy
```

Dabei werden Toolbeschreibungen eingebettet und semantisch gegen den Prompt gesucht.

---

## 23.8 Tool Execution Loop

Das Modell darf nicht unbegrenzt Tools aufrufen.

Pro Anfrage gelten harte Grenzen:

```text
maxToolCalls
maxRuntimeSeconds
maxToolResultChars
maxModelRetries
```

Default-Vorschlag:

```text
maxToolCalls = 8
maxRuntimeSeconds = 120
maxToolResultChars = 12000
maxModelRetries = 1
```

Stop-Gründe:

```text
completed
max_tool_calls
timeout
policy_denied
tool_error
model_error
cancelled
```

Toolergebnisse müssen vor Rückgabe an das Modell begrenzt werden:

```text
- lange Textausgaben kürzen
- große JSON-Antworten zusammenfassen oder abschneiden
- Binärdaten nicht an das Modell weitergeben
- Secrets maskieren
```

---

## 23.9 Ollama als Standard-Model-Backend

Der erste LangChain-Provider soll Ollama als lokales Modell-Backend unterstützen.

Konfiguration:

```json
{
  "id": "pyloros-ai",
  "type": "langchain",
  "prefix": "pyloros-ai/",
  "exposeInViews": ["public"],
  "agentToolView": "llm-agent",
  "model": {
    "provider": "ollama",
    "baseUrl": "http://localhost:11434",
    "modelName": "qwen2.5-coder:7b",
    "temperature": 0.1,
    "timeoutSeconds": 120
  },
  "toolSelection": {
    "strategy": "keyword",
    "maxTools": 25
  },
  "execution": {
    "maxToolCalls": 8,
    "maxRuntimeSeconds": 120,
    "maxToolResultChars": 12000,
    "requireApprovalForWriteTools": true
  }
}
```

Das Modell muss austauschbar bleiben.

Dafür keine direkte Ollama-Abhängigkeit in Domain-Klassen verwenden.

Richtig:

```text
domain
  kennt kein Ollama
  kennt kein LangChain4j

infrastructure.langchain
  kennt LangChain4j
  kennt Ollama
```

---

## 23.10 Policies für LLM-gesteuerte Toolaufrufe

LLM-gesteuerte Toolaufrufe sind riskanter als direkte Client-Toolcalls, weil das Modell die Toolauswahl selbst trifft.

Deshalb gelten für `LangChainVirtualToolProvider` strengere Regeln:

```text
read/search/status
  -> allow

write/edit/delete
  -> require approval

execute/shell
  -> require approval

network/external-url
  -> allowlist or approval

recursive-agent-call
  -> deny

unknown risk
  -> deny
```

Zusätzliche Regel:

```text
Der LangChain-Provider darf niemals Tools aus Providern aufrufen,
die in seiner agentToolView ausgeschlossen sind.
```

Der Policy-Kontext muss enthalten:

```text
originProviderId = pyloros-ai
originMode = llm
requestedToolName
targetProviderId
targetNativeToolName
riskLevel
```

---

## 23.11 Auditing für LLM-gesteuerte Toolaufrufe

Für jeden Natural-Language-Aufruf auditieren:

```text
timestamp
clientId
sessionId
providerId = pyloros-ai
promptHash
selectedToolCount
modelProvider
modelName
maxToolCalls
durationMs
stopReason
```

Für jeden vom Modell ausgelösten Toolcall auditieren:

```text
timestamp
clientId
sessionId
originProviderId = pyloros-ai
requestedToolName
targetProviderId
targetNativeToolName
argumentsHash
riskLevel
policyDecision
durationMs
resultState
```

Nicht loggen:

```text
- vollständige Prompts mit Secrets
- vollständige Toolargumente mit Tokens
- Authorization Header
- private Dateien
```

Optional loggen:

```text
- gekürzte Prompt-Vorschau
- gekürzte Antwort-Vorschau
- ausgewählte Toolnamen
```

---

## 23.12 Fehlerbehandlung

Fehlerarten:

```text
ModelUnavailable
  Ollama nicht erreichbar oder Modell fehlt

ModelTimeout
  Modell antwortet nicht rechtzeitig

ToolSelectionFailed
  keine passenden Tools gefunden

ToolExecutionDenied
  Policy verweigert Toolaufruf

ToolExecutionFailed
  Zieltool schlägt fehl

MaxToolCallsExceeded
  Modell überschreitet Toollimit

InvalidModelToolRequest
  Modell erzeugt ungültige Toolargumente
```

MCP-Mapping:

```text
ModelUnavailable
  -> ToolResult isError=true

ModelTimeout
  -> ToolResult isError=true

ToolExecutionDenied
  -> ToolResult isError=false with structuredContent.state="permission_required" oder isError=true bei harter Ablehnung

ToolExecutionFailed
  -> Modell darf einmal versuchen, mit Fehlerkontext zu antworten

MaxToolCallsExceeded
  -> ToolResult isError=false mit teilweiser Antwort und stoppedReason=max_tool_calls
```

---

## 23.13 Sessions

Für synchrone `ask`-Aufrufe reicht eine flüchtige Session.

Für längere Läufe braucht Pyloros eine Session-Ablage.

```text
LangChainTask
├─ taskId
├─ providerId
├─ state
├─ promptHash
├─ selectedTools
├─ usedTools
├─ startedAt
├─ updatedAt
├─ stopReason
├─ events
├─ pendingPermissions
└─ result
```

States:

```text
created
running
waiting_for_permission
completed
failed
cancelled
timeout
```

Diese Struktur soll bewusst ähnlich zu `AgentTask` für ACP sein, damit UI, Admin-Tools und Auditing später wiederverwendbar bleiben.

---

## 23.14 Runtime Flow: Natural-Language Toolzugriff

```text
Client
  ↓ tools/call pyloros-ai/ask
Pyloros McpServerRoutes
  ↓
CallToolUseCase
  ↓
ToolRouter
  ↓
ToolCatalog.resolveTool("pyloros-ai/ask")
  ↓
ProviderRegistry.getToolProvider("pyloros-ai")
  ↓
LangChainVirtualToolProvider.callTool("ask")
  ↓
LangChainAgentService.answer(...)
  ↓
ToolCatalog.listTools("llm-agent")
  ↓
ToolSelectionStrategy.selectTools(...)
  ↓
OllamaChatModel
  ↓ tool request
PylorosToolExecutor
  ↓
ToolRouter.callTool("intellij/get_problems")
  ↓
McpUpstreamToolProvider
  ↓
IntelliJ MCP Server
  ↓ result
OllamaChatModel
  ↓ final answer
LangChainVirtualToolProvider
  ↓ ToolCallResult
Client
```

---

## 23.15 Runtime Flow: Rekursion verhindern

```text
Client
  ↓ pyloros-ai/ask
LangChainVirtualToolProvider
  ↓ listTools("llm-agent")
```

Die `llm-agent` View darf nicht enthalten:

```text
pyloros-ai/*
copilot/*
goose/*
claude/*
```

Dadurch ist folgender Ablauf unmöglich:

```text
pyloros-ai/ask
  ↓
model calls pyloros-ai/ask
  ↓
model calls pyloros-ai/ask
  ↓
...
```

---

## 23.16 Resources für LangChain Provider

Neue Resources:

```text
pyloros://providers/pyloros-ai
pyloros://langchain/config
pyloros://langchain/tasks
pyloros://langchain/tasks/{taskId}
pyloros://langchain/tool-view
pyloros://langchain/selected-tools/{taskId}
```

Beispiel `pyloros://providers/pyloros-ai`:

```json
{
  "id": "pyloros-ai",
  "type": "langchain",
  "status": "available",
  "model": {
    "provider": "ollama",
    "baseUrl": "http://localhost:11434",
    "modelName": "qwen2.5-coder:7b"
  },
  "agentToolView": "llm-agent"
}
```

---

## 23.17 Prompts für Natural-Language Workflows

Der LangChain Provider kann wiederverwendbare MCP-Prompts veröffentlichen.

Beispiele:

```text
ask_workspace
inspect_project
summarize_open_pull_requests
diagnose_current_problem
explain_available_tools
```

Diese Prompts sollen nicht als harte Tools modelliert werden, sondern als MCP-Prompts, weil sie Anweisungen für wiederverwendbare Aufgaben sind.

---

## 23.18 Konfigurationserweiterung

Beispiel:

```json
{
  "views": {
    "public": {
      "includeProviders": [
        "pyloros",
        "intellij",
        "github",
        "intellij-index",
        "copilot",
        "pyloros-ai"
      ]
    },
    "llm-agent": {
      "includeProviders": [
        "pyloros",
        "intellij",
        "github",
        "intellij-index"
      ],
      "excludeProviders": [
        "pyloros-ai",
        "copilot",
        "goose",
        "claude"
      ]
    }
  },
  "providers": [
    {
      "id": "pyloros-ai",
      "type": "langchain",
      "prefix": "pyloros-ai/",
      "exposeInViews": ["public"],
      "agentToolView": "llm-agent",
      "model": {
        "provider": "ollama",
        "baseUrl": "http://localhost:11434",
        "modelName": "qwen2.5-coder:7b",
        "temperature": 0.1,
        "timeoutSeconds": 120
      },
      "toolSelection": {
        "strategy": "keyword",
        "maxTools": 25
      },
      "execution": {
        "maxToolCalls": 8,
        "maxRuntimeSeconds": 120,
        "maxToolResultChars": 12000,
        "requireApprovalForWriteTools": true
      }
    }
  ]
}
```

---

## 23.19 Startup Lifecycle Erweiterung

Zusätzlich zum bestehenden Startup:

```text
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

für LangChain ergänzen:

```text
- parse langchain provider configuration
- validate agentToolView exists
- validate agentToolView excludes the langchain provider itself
- probe Ollama baseUrl
- optionally probe configured model
- register pyloros-ai tools
- expose provider status as available/degraded/unavailable
```

Provider-Status:

```text
available
  Ollama erreichbar und Modell verfügbar

degraded
  Provider konfiguriert, aber Modellprüfung fehlgeschlagen

unavailable
  Ollama nicht erreichbar oder Konfiguration ungültig
```

Pyloros darf auch starten, wenn `pyloros-ai` unavailable ist.

---

## 23.20 Empfohlene Implementierungsstufe

### Milestone 7: LangChain Virtual Provider

```text
- ProviderType.LANGCHAIN ergänzen
- LangChainProviderConfiguration ergänzen
- LangChainVirtualToolProvider implementieren
- pyloros-ai/ask als erstes Tool veröffentlichen
- OllamaModelFactory implementieren
- ToolCatalogAdapter für llm-agent View implementieren
- PylorosToolSpecificationMapper implementieren
- PylorosToolExecutor über ToolRouter implementieren
- ToolExecutionLoop mit maxToolCalls und Timeout implementieren
- KeywordToolSelectionStrategy implementieren
- Policy-Kontext für originMode=llm ergänzen
- Audit-Einträge für LLM-gesteuerte Toolcalls ergänzen
```

### Milestone 8: LangChain Sessions

```text
- pyloros-ai/start implementieren
- pyloros-ai/get_status implementieren
- pyloros-ai/get_result implementieren
- pyloros-ai/cancel implementieren
- LangChainSessionRepository implementieren
- LangChainTask an AgentTask-Struktur angleichen
- Resources für pyloros://langchain/tasks ergänzen
```

### Milestone 9: Verbesserte Toolauswahl

```text
- Toolbeschreibungen normalisieren
- Provider- und Toolnamen stärker gewichten
- maxTools konfigurierbar machen
- optional EmbeddingToolSelectionStrategy vorbereiten
- große Toolkataloge nicht vollständig an das Modell senden
```

---

## 23.21 Designentscheidungen

| Entscheidung | Begründung |
| --- | --- |
| LangChain als virtueller Provider | Passt zum bestehenden Provider-Modell und hält MCP nach außen stabil. |
| Pyloros bleibt Tool-Hoheit | LangChain darf Tools auswählen, aber nicht Provider, Policies oder Routing umgehen. |
| Eigene `llm-agent` View | Verhindert Rekursion und reduziert die sichtbare Toolmenge. |
| Ollama als erstes Backend | Erlaubt lokale Ausführung auf eigener GPU ohne Cloud-Zwang. |
| ToolSelectionStrategy | Große Toolkataloge werden beherrschbar und das Modell bekommt nur relevante Werkzeuge. |
| ToolExecutionLoop mit Limits | Verhindert endlose Modell-/Tool-Schleifen. |
| Policy-Kontext `originMode=llm` | LLM-gesteuerte Aufrufe brauchen strengere Sicherheitsregeln. |
| LangChain nur in Infrastructure | Domain bleibt frei von LangChain4j- und Ollama-Abhängigkeiten. |
| Erst synchron `ask`, später Tasks | Schneller Einstieg, aber saubere Erweiterung für lange Agentenläufe. |

---

## 23.22 Kurzform

```text
Client
  ↓ MCP
Pyloros
  ↓
LangChainVirtualToolProvider
  ↓
LangChain4j + Ollama
  ↓
ToolCatalog(llm-agent view)
  ↓
ToolRouter
  ↓
Native / MCP / andere Provider
```

**Der LangChainVirtualToolProvider übersetzt natürliche Sprache in sichere, policy-geprüfte Toolcalls gegen den bestehenden aggregierten Pyloros-ToolCatalog.**
