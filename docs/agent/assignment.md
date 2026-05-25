# Issue #38 — R5-12: Integrationstests mit Fake-Model und Fake-Tools

Parent: aresstack/pyloros#26 (Release 5: LangChain/Ollama Virtual Provider).

## Ziel

LangChain-/LLM-Flows reproduzierbar testbar machen — komplette
`pyloros-ai/ask`-Kette mit Fake-Modell und Fake-Tools, damit CI-Tests ohne
Ollama und ohne Netzwerkzugriff stabil und schnell laufen.

## Umfang (laut Issue)

- Fake-Modell fuer deterministische Antworten bereitstellen.
- Fake-Modell kann Toolcalls anfordern.
- Fake-Modell kann Modellfehler simulieren.
- Fake-Tools ueber bestehenden ToolProvider/ToolRouter bereitstellen.
- Integrationstest fuer `ask` Happy Path.
- Integrationstest fuer Toolfehler und Modellfehler.

## Vorgeschlagene Testkomponenten

```text
FakeChatModel
FakeToolCallingModel
FakeLangChainToolProvider
LangChainAskIntegrationTest
```

## Akzeptanzkriterien

- Tests laufen ohne Ollama.
- Tests laufen ohne Netzwerkzugriff.
- Happy Path mit einem Toolcall ist abgedeckt.
- Mehrere Toolcalls sind abgedeckt.
- Modellfehler ist abgedeckt.
- Toolfehler ist abgedeckt.
- Limits wie `maxToolCalls` und Timeout sind testbar.

## Geforderte Tests

- `ask` beantwortet Frage ohne Tool.
- `ask` verwendet ein Fake-Tool.
- `ask` verwendet mehrere Fake-Tools.
- Fake-Modell wirft Fehler.
- Fake-Tool liefert `isError=true`.
- `maxToolCalls` stoppt Loop.
- ToolResult wird gekuerzt.

## Architekturregeln

- Java 21, Vert.x, Gradle Groovy DSL.
- Tool-Aggregation ueber `ToolProvider` und `ToolRegistry`/`ToolRouter`.
- Optionale Upstreams duerfen Start nicht verhindern.
- Kein Spring.

Kein Commit ohne Freigabe.
