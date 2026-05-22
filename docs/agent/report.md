# Task 002-G Report - Refresh Token Rejection Diagnostics

**Date:** 2026-05-22  
**Assignee:** GitHub Copilot  
**Status:** ✅ **SUCCESSFUL - COMMITTED AND PUSHED**

---

## Was wurde verifiziert, geändert oder implementiert?

### Root Cause (aus Logs identifiziert)
```
23:02:19  Loaded 1 refresh tokens from data\oauth-refresh-tokens.json
23:03:08  grantType=refresh_token
23:03:08  response status=400 error=invalid_grant
```
Der Refresh Token wurde aus dem Store geladen, beim Exchange aber abgelehnt.  
Ohne Diagnose-Logging unklar ob: TTL abgelaufen? Client-Mismatch? Token unbekannt?

### Änderung 1: `exchangeFromRefreshToken()` — 4 interne Ablehnungsgründe

Vorher (alle 4 Fälle gleich):
```
[OAUTH] token response status=400 grant_type=refresh_token error=invalid_grant
```

Nachher (jeder Fall mit eigenem Grund):
```
[OAUTH] refresh rejected reason=missing_refresh_token clientId=chatgpt
[OAUTH] refresh rejected reason=unknown_refresh_token clientId=chatgpt token=a1b2c3d4...
[OAUTH] refresh rejected reason=expired_refresh_token clientId=chatgpt expiredAt=2026-05-22T22:53:19Z
[OAUTH] refresh rejected reason=client_mismatch expected=chatgpt actual=other-client
```

**Sicherheit:** Externe Response bleibt unverändert → `{"error":"invalid_grant"}`  
**Token-Werte:** Nur `shortValue()` (8 Zeichen + "...") für `unknown_refresh_token`

### Änderung 2: `loadRefreshTokensFromStore()` — Expired-Tokens beim Laden entfernen

**Vorher:**
```
[OAUTH] Loaded 1 refresh tokens from data\oauth-refresh-tokens.json
```
→ Kein Hinweis ob der geladene Token noch gültig ist

**Nachher:**
```
[OAUTH] Refresh token store loaded=1 expired_removed=1 active=0 from data\oauth-refresh-tokens.json
[OAUTH] refresh token expired on load clientId=chatgpt expiredAt=2026-05-22T22:53:19Z
```
→ Sofort sichtbar: abgelaufene Tokens werden beim Start **nicht in den aktiven Store** übernommen  
→ Store wird automatisch bereinigt (Token-Datei ohne abgelaufene Einträge übergeschrieben)

---

## Welche Dateien wurden geändert oder neu erstellt?

**Modified:**
1. `src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
   - `exchangeFromRefreshToken()`: 4 Log-Statements mit spezifischem `reason=`
   - `loadRefreshTokensFromStore()`: Expired-Check beim Laden, neues Summary-Log, Store-Cleanup
2. `docs/agent/assignment.md` - Task 002-G Assignment
3. `docs/agent/report.md` - Dieser Report

---

## Welche Architekturentscheidung wurde berührt?

- **OAuth-Logik bleibt in `OAuthService`** — nur Logging verbessert, keine API-Änderung
- **Store-Cleanup bei Startup** — abgelaufene Token werden nicht mehr in Memory geladen
- **Abwärtskompatibel** — externe Response bleibt `{"error":"invalid_grant"}`

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

### Log-Beispiele (erwartet nach Neustart)

**Normaler Start mit gültigem Store:**
```
[OAUTH] Refresh token store loaded=1 expired_removed=0 active=1 from data\oauth-refresh-tokens.json
```

**Start mit TTL=300s und altem Token:**
```
[OAUTH] refresh token expired on load clientId=chatgpt expiredAt=2026-05-22T22:53:19Z
[OAUTH] Refresh token store loaded=1 expired_removed=1 active=0 from data\oauth-refresh-tokens.json
```

**Refresh-Request mit abgelaufenem Token (Rest-Fall, falls im RAM):**
```
[OAUTH] refresh rejected reason=expired_refresh_token clientId=chatgpt expiredAt=2026-05-22T22:53:19Z
```

---

## Ergebnis: ✅ erfolgreich

---

## Falls fehlgeschlagen: exakter Fehler und nächster Schritt

Kein Fehler.

---

## Exact commit hash

**Commit:** `70b3d07`  
**Message:** "feat(oauth): add refresh token rejection diagnostics and startup store summary (002-G)"  
**Remote:** `origin/main` (be07aef..70b3d07)

---

## Empfohlene TTL für Restart-Tests

```powershell
$env:OAUTH_ACCESS_TOKEN_TTL_SECONDS="30"
$env:OAUTH_REFRESH_TOKEN_TTL_SECONDS="3600"
```

→ Access Token: 30s (kurzlebig → Refresh schnell sichtbar)  
→ Refresh Token: 60 Minuten (überlebt Testfenster)  
→ Bei nächstem Neustart erscheint im Log: `active=1` (Token noch gültig)
