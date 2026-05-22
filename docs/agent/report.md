# Agent Report

## Was wurde verifiziert, geändert oder implementiert?

**001-D2 - Normalize upstream tool names** wurde umgesetzt.

Implementiert:

- Öffentliche IDEA-Toolnamen wurden auf `intellij/...` umgestellt.
- Legacy-Calls mit `idea__...` bleiben als Alias lauffähig.
- Unprefixed Calls (z. B. `get_project_modules`) werden als Kompatibilitäts-Alias akzeptiert, aber nur wenn der Name ein bekannter IDEA-Upstream-Toolname ist.
- Intern wird immer auf den originalen IDEA-Namen ohne Prefix weitergeleitet.
- `tools/list` liefert weiterhin nur eine saubere öffentliche Variante (keine Duplikate, keine unprefixed Aliasnamen).

## Welche Dateien wurden geändert oder neu erstellt?

- `src/main/java/com/aresstack/pyloros/upstream/idea/IdeaToolNameMapper.java` (neu)
  - zentrale Namensnormalisierung:
    - `publicName(original)` -> `intellij/original`
    - `toOriginalName(aliasOrPublicOrOriginal)` -> `original`
    - Alias-Erkennung (`intellij/`, `idea__`)
- `src/main/java/com/aresstack/pyloros/upstream/idea/IdeaMcpClient.java`
  - `tools/list`-Normalisierung auf `intellij/...`
  - Tracking bekannter Original-Toolnamen (`knownOriginalToolNames`)
  - neue Methoden: `hasKnownTools()`, `isKnownOriginalTool(...)`
- `src/main/java/com/aresstack/pyloros/upstream/idea/IdeaToolProvider.java`
  - `supports(...)` erweitert für:
    - `intellij/...` (primär)
    - `idea__...` (legacy)
    - unprefixed Alias (optimistisch bis Warm-up, danach strikt nur bekannte Tools)
  - `callTool(...)` normalisiert Namen robust auf Originalnamen und validiert unprefixed Namen gegen bekannte Upstream-Tools
- `docs/agent/report.md` (überschrieben)

## Welche Architekturentscheidung wurde berührt?

- Public naming scheme für IDEA-Tools in Pyloros:
  - alt: `idea__...`
  - neu (primär): `intellij/...`
- Kompatibilitätsstrategie:
  - Listung nur in einem kanonischen Schema (`intellij/...`)
  - Aufruf-Kompatibilität für `idea__...` und bekannte unprefixed Namen ohne doppelte Tool-Listeneinträge

## Welche Tests, Builds und Runtime-Checks wurden ausgeführt?

1. **Build (JDK 21)**
   - `./gradlew --no-daemon build --stacktrace`
   - Ergebnis: **BUILD SUCCESSFUL**

2. **Runtime-Start**
   - Pyloros auf `SERVER_PORT=8082` mit `OAUTH_ACCESS_TOKEN=dev-token` gestartet

3. **tools/list Verifikation**
   - `tools/list` liefert:
     - `pyloros__ping`
     - IDEA-Tools nur als `intellij/...`
   - Keine `idea__...`-Duplikate in der Liste

4. **tools/call Alias-Kompatibilität**
   - `intellij/get_project_modules` -> funktioniert (`isError=false`, echte IDEA-Daten)
   - `idea__get_project_modules` -> funktioniert (`isError=false`, echte IDEA-Daten)
   - `get_project_modules` -> funktioniert (`isError=false`, echte IDEA-Daten)

5. **Akzeptanztest gemäß Auftrag (`get_file_text_by_path`)**
   - `intellij/get_file_text_by_path` -> funktioniert (`isError=false`)
   - `idea__get_file_text_by_path` -> funktioniert (`isError=false`)
   - `get_file_text_by_path` -> funktioniert (`isError=false`)

6. **Negative Validierung (nur bekannte unprefixed akzeptieren)**
   - `totally_unknown_tool_name` -> kontrolliert abgelehnt mit `isError=true` und Text `Unsupported tool: totally_unknown_tool_name`

## Ergebnis: erfolgreich

- 001-D2 Scope erfüllt
- Build grün
- Runtime-Verifikation grün
- Keine Scope-Erweiterung (kein Index-MCP, keine UI, keine Persistenz)

## Fehlgeschlagen: entfällt

Kein Fehlerfall offen.

## Exact commit hash, or No commit created

`adb7ea9` (kein Push durchgeführt)
