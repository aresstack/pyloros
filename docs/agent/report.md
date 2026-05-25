# Report: R5-01 LangChain Provider-Konfiguration

## Was wurde umgesetzt?

LangChain/Ollama Provider-Konfiguration für Pyloros implementiert.
Ein Provider mit `type: "langchain"` wird aus `mcp.json` erkannt,
validiert und als `LangChainProviderConfiguration` geladen.

## Geänderte / neu erstellte Dateien

Neu:

- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/LangChainProviderConfiguration.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/OllamaConfiguration.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/LangChainExecutionConfiguration.java`
- `pyloros-server/src/main/java/com/aresstack/pyloros/langchain/LangChainToolViewValidator.java`
- `pyloros-app/src/main/java/com/aresstack/pyloros/config/LangChainProviderJsonConfig.java`
- `pyloros-app/src/main/java/com/aresstack/pyloros/config/LangChainProviderFactory.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/langchain/LangChainProviderConfigurationTest.java`
- `pyloros-server/src/test/java/com/aresstack/pyloros/langchain/LangChainToolViewValidatorTest.java`

Geändert:

- `pyloros-app/src/main/java/com/aresstack/pyloros/config/McpJsonConfig.java` — `langchainProviders` Feld
- `pyloros-app/src/main/java/com/aresstack/pyloros/PylorosApplication.java` — LangChain-Konfiguration laden

## Berührte Architekturentscheidung

- Folgt dem ACP-Provider-Pattern: JSON-Config-Record, Domain-Config-Record, Factory, Validator.
- `LangChainProviderConfiguration` in `pyloros-server` (Domain); JSON-Config/Factory in `pyloros-app` (Wiring).
- `ProviderType.LANGCHAIN` war bereits im Enum vorhanden.
- Eigentliche ToolProvider-Implementierung wird im Runtime-Issue erstellt.

## Tests / Builds

- `./gradlew --no-daemon :pyloros-server:compileJava :pyloros-server:compileTestJava :pyloros-app:compileJava` → BUILD SUCCESSFUL
- `./gradlew --no-daemon :pyloros-server:test` → BUILD SUCCESSFUL (alle Tests bestanden)

## Ergebnis: Erfolgreich
