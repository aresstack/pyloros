# Task 002-H Report - Auth Failure Signalling (RFC 6750)

**Date:** 2026-05-22  
**Assignee:** GitHub Copilot  
**Status:** ✅ **SUCCESSFUL - COMMITTED AND PUSHED**

---

## Was wurde verifiziert, geändert oder implementiert?

### Ziel
ChatGPT den bestmöglichen RFC-konformen Signal geben, dass ein Access Token ungültig ist —
damit der Connector in den Reconnect-Pfad geht statt stillzuschweigen.

### Änderung 1: `BearerAuthResult` Enum (neues Domain-Objekt)

```java
// domain/oauth/BearerAuthResult.java
public enum BearerAuthResult {
    OK,             // Token gültig
    MISSING_TOKEN,  // Kein Authorization-Header / kein Bearer
    INVALID_TOKEN,  // Unbekannter Token
    EXPIRED_TOKEN   // Token abgelaufen
}
```

### Änderung 2: `OAuthService.checkBearerAuth()` (neue Methode)

Unterscheidet jetzt alle 4 Fälle statt nur `true/false`.  
`isBearerAuthorized()` bleibt als Delegation erhalten (rückwärtskompatibel).

### Änderung 3: `McpRoutes` — RFC 6750 401 Response

**Vorher:**
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
Cache-Control: no-store

{"error":"Unauthorized"}
```

**Nachher (RFC 6750, Section 3.1):**
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
Cache-Control: no-store
Pragma: no-cache
WWW-Authenticate: Bearer error="invalid_token", error_description="The access token is invalid or expired"

{"error":"invalid_token"}
```

**Gilt für:** GET /sse, POST /sse — alle Fälle (missing, invalid, expired)

### Logging in McpRoutes (ohne Tokenwerte)
```
[MCP] auth rejected reason=missing_token
[MCP] auth rejected reason=invalid_token
[MCP] auth rejected reason=expired_token
```

---

## Welche Dateien wurden geändert oder neu erstellt?

**Neu erstellt:**
1. `src/main/java/com/aresstack/pyloros/domain/oauth/BearerAuthResult.java`

**Modified:**
2. `src/main/java/com/aresstack/pyloros/oauth/OAuthService.java`
   - Import BearerAuthResult hinzugefügt
   - `checkBearerAuth()` neue Public-Methode
   - `isBearerAuthorized()` delegiert auf `checkBearerAuth()`
3. `src/main/java/com/aresstack/pyloros/http/McpRoutes.java`
   - Import BearerAuthResult und Logger hinzugefügt
   - `WWW_AUTHENTICATE_INVALID_TOKEN` Konstante
   - `mcpSse()` und `mcpPost()` nutzen `checkBearerAuth()` statt `isBearerAuthorized()`
   - `unauthorized(context, BearerAuthResult)` mit RFC-6750-konformer Response
4. `docs/agent/assignment.md` - Task 002-H Assignment
5. `docs/agent/report.md` - Dieser Report

---

## Welche Architekturentscheidung wurde berührt?

- **`domain/oauth/`** — neues Domain-Objekt `BearerAuthResult` (enum)
- **`OAuthService`** — `checkBearerAuth()` als neue präzise API
- **`McpRoutes`** — HTTP-Layer nutzt BearerAuthResult-Enum → separation of concerns bleibt
- **`/oauth/token`** bleibt unverändert → `400 invalid_grant` für Refresh-Token-Fehler (RFC 6749 korrekt)

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

### Conformance Check
```
✅ HTTP 401 bei ungültigem/fehlendem/abgelaufenem Access Token
✅ WWW-Authenticate: Bearer error="invalid_token", error_description="..."
✅ Content-Type: application/json
✅ Cache-Control: no-store
✅ Pragma: no-cache
✅ Body: {"error":"invalid_token"}
✅ /oauth/token bleibt 400 invalid_grant (unverändert)
✅ [MCP] auth rejected reason=... im Log (kein Tokenwert)
```

---

## Ergebnis: ✅ erfolgreich

---

## Falls fehlgeschlagen: exakter Fehler und nächster Schritt

Kein Fehler.

---

## Exact commit hash

**Commit:** `2ea1233`  
**Message:** "feat(auth): RFC 6750 WWW-Authenticate on 401 and BearerAuthResult enum (002-H)"  
**Remote:** `origin/main` (d7aabc7..2ea1233)

---

## Was ChatGPT jetzt sieht

Nach abgelaufenem Refresh Token:
1. `POST /oauth/token` → `400 {"error":"invalid_grant"}` ← OAuth-korrekt
2. Nächster Toolcall mit altem Access Token → `401 {"error":"invalid_token"}` + `WWW-Authenticate` ← RFC 6750

ChatGPT hat jetzt beide RFC-konformen Signale:
- Token-Endpunkt: `invalid_grant` → Refresh nicht möglich
- Ressourcen-Endpunkt: `invalid_token` + `WWW-Authenticate` → Token abgelaufen/ungültig
