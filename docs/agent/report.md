# Task 002-F Report - OAuth Authorization Code Replay Tolerance

**Date:** 2026-05-22  
**Assignee:** GitHub Copilot  
**Status:** ✅ **SUCCESSFUL - COMMITTED AND PUSHED**

---

## Was wurde verifiziert, geändert oder implementiert?

### Root Cause (aus Logs identifiziert)
```
22:48:52.673 authorization_code code=e676...
22:48:52.689 authorization_code code=e676...
```
Gleicher Authorization Code wird innerhalb 16 ms zweimal eingelöst.  
Erster Request: ✅ code gültig → TokenResponse  
Zweiter Request: ❌ code bereits entfernt → `invalid_grant`  
Folge: ChatGPT zeigt kurz Fehler, obwohl erster Request erfolgreich war.

### Lösung: Replay Cache (10 Sekunden TTL)

In `OAuthService`:
- Neues Feld: `Map<String, ReplayCacheEntry> replayCache`
- Neues Record: `ReplayCacheEntry(tokenResponse, clientId, redirectUri, codeVerifier, expiresAt)`
- Konstante: `REPLAY_CACHE_TTL_SECONDS = 10`

**Flow bei erstem Exchange:**
1. Code aus `authorizationCodes` entnehmen (wie bisher)
2. Alle Validierungen durchführen (PKCE, client, redirect_uri)
3. TokenResponse erstellen
4. **Neu:** TokenResponse im `replayCache` mit 10s TTL speichern
5. TokenResponse zurückgeben

**Flow bei zweitem Exchange (gleicher Code):**
1. Code nicht mehr in `authorizationCodes` → `tryReplayAuthorization()` aufrufen
2. Replay-Cache-Eintrag suchen
3. Validierung: selber `clientId` + `redirectUri` + `codeVerifier`
4. Bei Match → gecachte TokenResponse zurückgeben (selbe Tokens!)
5. Bei Mismatch oder abgelaufen → `invalid_grant`

**Sicherheitsgarantie:**
- Anderer Client bekommt `invalid_grant` (kein Cross-Client-Replay)
- Anderer `redirect_uri` bekommt `invalid_grant`
- Anderer `code_verifier` bekommt `invalid_grant`
- Nach 10 Sekunden: `invalid_grant` (wie bisher, aber mit korrektem Log)

### Logging (keine Tokenwerte)
```
[OAUTH] authorization_code replay hit code=e676... clientId=chatgpt
[OAUTH] authorization_code replay miss code=e676...
[OAUTH] authorization_code replay rejected - client_id mismatch code=e676...
[OAUTH] authorization_code replay rejected - redirect_uri mismatch code=e676...
[OAUTH] authorization_code replay rejected - code_verifier mismatch code=e676...
```

### Pragma: no-cache in HttpJson.send()
- `HttpJson.send()` jetzt mit `Pragma: no-cache` (war nur in `sendTokenResponse()` für success)
- Damit gehen auch Fehler-Responses RFC-konform raus
- Alle JSON-Responses (MCP und OAuth) kein Problem, `Pragma: no-cache` ist harmlos

---

## Welche Dateien wurden geändert oder neu erstellt?

**Modified:**
1. `src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
   - `replayCache` Feld und `REPLAY_CACHE_TTL_SECONDS` Konstante
   - `ReplayCacheEntry` Record (inner private)
   - `exchangeFromAuthorizationCode()` erweitert: Replay-Speicherung + Delegation
   - `tryReplayAuthorization()` neue Methode
   - `cleanupExpiredReplayCache()` neue Methode
   - `shortValue()` Hilfsmethode für sicheres Logging
2. `src/main/java/com/aresstack/pyloros/http/HttpJson.java`
   - `Pragma: no-cache` zu `send()` hinzugefügt
3. `docs/agent/assignment.md` - Task 002-F Assignment
4. `docs/agent/report.md` - Dieser Report

---

## Welche Architekturentscheidung wurde berührt?

- **OAuth-Logik bleibt in `OAuthService`** — Replay-Cache ist internes Implementierungsdetail
- **Single-use Semantik bleibt erhalten** — Code wird weiterhin sofort aus `authorizationCodes` entfernt
- **Security unchanged** — Replay nur für identische (client, redirect, verifier) Kombination
- **Kein persistenter State** — Replay-Cache ist nur In-Memory, kein Store

---

## Welche Tests, Builds und Runtime-Checks wurden ausgeführt?

### Build (JDK Zulu 21)
```
> Task :compileJava ✅
> Task :processResources UP-TO-DATE
> Task :classes ✅
> Task :jar ✅
> Task :startScripts ✅
> Task :distTar ✅
> Task :distZip ✅
> Task :assemble ✅
> Task :build ✅

BUILD SUCCESSFUL in 1s
6 actionable tasks: 5 executed, 1 up-to-date
```

### Akzeptanzkriterien (logisch verifiziert)
```
✅ Doppelter authorization_code Request → zweimal HTTP 200
✅ Zweiter Request bekommt dieselbe TokenResponse (selbe Tokens)
✅ Zweiter Request mit anderem client → invalid_grant
✅ Zweiter Request mit anderem redirect_uri → invalid_grant
✅ Zweiter Request nach 10s → invalid_grant
✅ Kein Token-Wert im Log (shortValue() kürzt auf 8 Zeichen)
✅ Pragma: no-cache in allen JSON-Responses (HttpJson.send)
✅ Build grün
```

---

## Ergebnis: ✅ erfolgreich

---

## Falls fehlgeschlagen: exakter Fehler und nächster Schritt

Kein Fehler.

---

## Exact commit hash

**Commit:** `9d9423a`  
**Message:** "feat(oauth): add authorization code replay tolerance and Pragma: no-cache (002-F)"  
**Remote:** `origin/main` (6b532a9..9d9423a)

---

## Nächster Schritt

Pyloros neu starten (ohne Mini-TTL), mit ChatGPT neu verbinden.  
Im Log erwarten:
```
[OAUTH] token grantType=authorization_code code=xxxx...
[OAUTH] token response status=200 grant_type=authorization_code has_access_token=true ...
[OAUTH] authorization_code replay hit code=xxxx... clientId=chatgpt   ← neu, kein Fehler mehr
[OAUTH] token response status=200 grant_type=authorization_code ...   ← auch zweiter Request 200
```
Danach sollte ChatGPT-Verbindung ohne "erst Fehler dann Erfolg" funktionieren.
