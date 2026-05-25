# Issue R5-06 — ToolSelectionStrategy MVP

## Was wurde implementiert?

- `ToolSelectionStrategy` Interface in neuem Package `com.aresstack.pyloros.langchain`.
- `ToolSelectionResult` Record (selectedTools, reason, fallback).
- `KeywordToolSelectionStrategy` als MVP: Tokenisierung von Frage und
  Tool-Name + Beschreibung, Scoring durch Keyword-Overlap, deterministische
  Sortierung (Score absteigend, dann Name aufsteigend), Limit über
  `maxTools`, Ausschlussliste `excludedTools`, Fallback bei leerer Eingabe
  oder Score 0.
- Strategie ist austauschbar (Interface) und arbeitet auf der von
  `ToolCatalog.listTools(ToolView.LLM_AGENT)` gelieferten
  `List<Map<String,Object>>` — die View-Filterung bleibt
  Caller-Verantwortung wie in `docs/requirements/pyloros-langchain-extension.md`
  vorgesehen.

## Geänderte / neue Dateien

- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/ToolSelectionStrategy.java` (neu)
- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/ToolSelectionResult.java` (neu)
- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/KeywordToolSelectionStrategy.java` (neu)
- `pyloros-server/src/test/java/com/aresstack/pyloros/langchain/KeywordToolSelectionStrategyTest.java` (neu)

## Architekturentscheidung

Neues Package `com.aresstack.pyloros.langchain` in `pyloros-server`. Keine
neuen Abhängigkeiten. Kein Eingriff in `PylorosApplication`, `ToolCatalog`,
`ToolRegistry` oder `ToolProvider` — die Strategie ist eine pure Funktion
über Tool-Deskriptoren und wird später vom `LangChainAgentService`
verwendet.

## Tests / Builds

- `./gradlew --no-daemon :pyloros-server:test --tests 'com.aresstack.pyloros.langchain.*'` — 11/11 grün.
- `./gradlew --no-daemon :pyloros-server:test` (gesamtes Modul) — BUILD SUCCESSFUL.

Abgedeckte Akzeptanzkriterien:
- GitHub-Frage wählt GitHub-Tools.
- IntelliJ-Frage wählt IntelliJ-Tools.
- Unbekannte / leere Frage liefert Fallback.
- Max-Tool-Limit wird eingehalten.
- Ausgeschlossene Tools werden nicht ausgewählt.
- Deterministische Auswahl.
- Strategie ist austauschbar (Lambda-Test).
- Ergebnis ist immutable.
- `maxTools <= 0` wird abgelehnt.

## Ergebnis

Erfolgreich. Kein Commit erstellt (folgt durch report_progress).
