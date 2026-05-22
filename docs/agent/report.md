# Agent Report

## Was wurde verifiziert, geändert oder implementiert?

`002-D - Persistent refresh token store` wurde umgesetzt.

Implementiert:

- Refresh Tokens werden nicht mehr nur in-memory gehalten, sondern zusätzlich in einer JSON-Datei persistiert.
- Store-Pfad ist konfigurierbar:
  - Property: `oauth.refresh-token.store.path`
  - Env: `OAUTH_REFRESH_TOKEN_STORE_PATH`
  - Default: `data/oauth-refresh-tokens.json`
- Beim Start lädt `OAuthService` vorhandene Refresh Tokens aus der Datei.
- Bei allen relevanten Änderungen wird der Store aktualisiert:
  - neuer Refresh Token (authorization_code)
  - rotierter Refresh Token
  - entfernte/ungültige/abgelaufene Refresh Tokens
- Abgelaufene Refresh Tokens werden beim Laden/Runtime-Cleanup entfernt und der Store wird bereinigt.
- Access Tokens bleiben weiterhin nur in-memory.

Nicht implementiert (wie gefordert):

- Keine Datenbank
- Kein Device Flow
- Keine UI
- Kein Push

## Welche Dateien wurden geändert oder neu erstellt?

- `src/main/java/com/aresstack/pyloros/config/PylorosConfig.java`
  - neues Feld `oauthRefreshTokenStorePath`
  - Laden von Property/Env für Store-Pfad
- `src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
  - Laden/Speichern des Refresh-Token-Stores (JSON)
  - persistente Struktur (`RefreshTokenStoreDocument`, `RefreshTokenEntry`)
  - Save-on-change bei add/rotate/remove/cleanup
- `src/main/resources/application.properties`
  - `oauth.refresh-token.store.path=data/oauth-refresh-tokens.json`
- `docs/agent/report.md` (überschrieben)

## Welche Architekturentscheidung wurde berührt?

- Refresh Tokens sind jetzt prozessübergreifend persistent (Datei-Store), während Access Tokens bewusst flüchtig bleiben.
- Persistenz bleibt minimal (JSON-Datei), ohne Einführung externer Infrastruktur.

## Welche Tests, Builds und Runtime-Checks wurden ausgeführt?

1. **Build (JDK 21)**
   - `./gradlew --no-daemon clean build --stacktrace`
   - Ergebnis: **BUILD SUCCESSFUL**

2. **Persistenztest (Neustart ohne Verlust)**
   - Start auf `8089` mit `OAUTH_REFRESH_TOKEN_STORE_PATH=data/oauth-refresh-tokens-002d.json`
   - OAuth Login durchgeführt (`authorization_code`)
   - Verifiziert:
     - Store-Datei existiert
     - Store-Datei enthält den erzeugten `refresh_token`
   - Pyloros neu gestartet mit gleicher Konfiguration
   - Log zeigt Laden des Stores: `[OAUTH] Loaded 1 refresh tokens ...`
   - Mit gespeichertem Refresh Token nach Neustart neuer Access Token erfolgreich erhalten
   - MCP Regression: `POST /sse tools/list` mit refreshed Access Token funktioniert (`mcp_tools_count=23`)

3. **Ablauf-/Cleanup-Test (kurze TTL)**
   - Start auf `8091` mit:
     - `OAUTH_REFRESH_TOKEN_STORE_PATH=data/oauth-refresh-exp-002d.json`
     - `OAUTH_REFRESH_TOKEN_TTL_SECONDS=2`
   - Refresh Token erzeugt und in Datei bestätigt
   - Nach Ablauf und Neustart:
     - Log zeigt Laden aus Store
     - Store ist bereinigt (`tokens_before_call=0`)
     - Refresh mit altem Token liefert `{"error":"invalid_grant"}`
     - Store bleibt bereinigt (`tokens_after_call=0`)

## Ergebnis: erfolgreich

- Build grün
- OAuth Login schreibt Refresh Token in Store-Datei
- Neustart verliert Refresh Token nicht
- Refresh nach Neustart funktioniert
- Abgelaufene Refresh Tokens werden entfernt und liefern `invalid_grant`
- MCP tools/list funktioniert nach Refresh weiter

## Falls fehlgeschlagen: exakter Fehler und nächster Schritt

- Kein offener Fehler im Scope 002-D.

## Konflikte / Hinweise

- `docs/agent/assignment.md` steht weiterhin auf älterem Task (001-D).
- Umsetzung erfolgte gemäß expliziter Nutzerfreigabe für `002-D`.

## Exact commit hash, or No commit created

- Commit wird nach erfolgreicher Verifikation erstellt.
