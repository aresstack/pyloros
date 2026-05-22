# Agent Report

## Was wurde verifiziert, geändert oder implementiert?

`002-B - Refresh token configuration and cleanup` wurde umgesetzt.

Implementiert:

- TTL-Konfiguration für Access Tokens und Refresh Tokens ergänzt:
  - Access Token TTL default: `3600` Sekunden
  - Refresh Token TTL default: `2592000` Sekunden
- Refresh-Token-Ablaufzeit wird serverseitig geprüft.
- Abgelaufene Refresh Tokens werden mit `invalid_grant` abgelehnt.
- Abgelaufene/ungültige Refresh Tokens werden aus dem In-Memory-Store entfernt.
- Optionales Rotation-Pattern wurde vorbereitet, aber nicht aktiviert (`REFRESH_TOKEN_ROTATION_ENABLED=false`).

Nicht implementiert (wie gefordert):

- kein persistenter Store
- kein Device Flow
- keine UI
- kein Push

## Welche Dateien wurden geändert oder neu erstellt?

- `src/main/java/com/aresstack/pyloros/config/PylorosConfig.java`
  - neue Konfigurationswerte:
    - `oauthAccessTokenTtlSeconds`
    - `oauthRefreshTokenTtlSeconds`
  - Property/Env-Lookup mit Defaults:
    - `oauth.access-token.ttl.seconds` / `OAUTH_ACCESS_TOKEN_TTL_SECONDS` (default 3600)
    - `oauth.refresh-token.ttl.seconds` / `OAUTH_REFRESH_TOKEN_TTL_SECONDS` (default 2592000)
- `src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
  - RefreshTokenState um `expiresAt` erweitert
  - Ablaufprüfung im Refresh-Grant ergänzt
  - Cleanup-Logik für abgelaufene Refresh Tokens ergänzt
  - invalid/mismatched Refresh Tokens werden entfernt und mit `invalid_grant` beantwortet
  - Access Token TTL aus Konfiguration statt Hardcode
  - vorbereitete, deaktivierte Rotation
- `src/main/resources/application.properties`
  - Default-TTLs ergänzt
- `docs/agent/report.md` (überschrieben)

## Welche Architekturentscheidung wurde berührt?

- OAuth Token Lifecycle wurde um konfigurierbare Expiration und In-Memory-Cleanup erweitert, ohne Persistenz einzuführen.
- Refresh Token Rotation bleibt absichtlich vorbereitet, aber deaktiviert, um den Scope klein und stabil zu halten.

## Welche Tests, Builds und Runtime-Checks wurden ausgeführt?

1. **Build (JDK 21)**
   - `./gradlew --no-daemon clean build --stacktrace`
   - Ergebnis: **BUILD SUCCESSFUL**

2. **Runtime-Testinstanz**
   - Pyloros auf `SERVER_PORT=8086` gestartet
   - Testkonfiguration:
     - `OAUTH_ACCESS_TOKEN_TTL_SECONDS=3600`
     - `OAUTH_REFRESH_TOKEN_TTL_SECONDS=2`

3. **Authorization Code Flow**
   - `/oauth/authorize` liefert Redirect mit `code`
   - `/oauth/token` mit `grant_type=authorization_code` funktioniert weiter
   - Token Response enthält `refresh_token`

4. **Refresh Grant (gültig)**
   - `/oauth/token` mit `grant_type=refresh_token` + gültigem Token liefert neuen `access_token`

5. **Refresh Grant (abgelaufen)**
   - Nach Wartezeit > TTL (`3s` bei `2s` TTL):
   - `/oauth/token` mit ursprünglichem Refresh Token liefert `{"error":"invalid_grant"}`

6. **Refresh Grant (ungültig)**
   - `/oauth/token` mit ungültigem Refresh Token liefert `{"error":"invalid_grant"}`

7. **MCP Regression nach Refresh**
   - Mit per Refresh erhaltenem Access Token:
   - `POST /sse` `tools/list` funktioniert (`mcp_tools_count=23`)

## Ergebnis: erfolgreich

- Build grün
- Refresh Token funktioniert mit gültigem Token
- Abgelaufener Refresh Token liefert `invalid_grant`
- Ungültiger Refresh Token liefert `invalid_grant`
- OAuth Authorization-Code Login funktioniert weiter
- MCP tools/list funktioniert nach Refresh weiter

## Falls fehlgeschlagen: exakter Fehler und nächster Schritt

- Kein offener Fehler im Scope 002-B.

## Konflikte / Hinweise

- `docs/agent/assignment.md` steht weiterhin auf einem älteren Task (001-D).
- Umsetzung erfolgte gemäß expliziter Nutzerfreigabe für `002-B`.

## Exact commit hash, or No commit created

- Commit wird nach erfolgreicher Verifikation erstellt.
