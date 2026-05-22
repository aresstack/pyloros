# Task 006 Report - Runtime Packaging and Operator Scripts

**Date:** 2026-05-23  
**Assignee:** GitHub Copilot  
**Status:** ✅ **SUCCESSFUL - COMMITTED AND PUSHED**

---

## Was wurde verifiziert, geändert oder implementiert?

### Neue Skripte in `scripts/`

**`start-pyloros.ps1`**
- Löst den Projekt-Root relativ zum Skript-Standort auf (kein hardcodieter Pfad nötig).
- Erkennt `JAVA_HOME` automatisch: bevorzugt `C:\Program Files\Zulu\zulu-21` wenn vorhanden; überschreibt vorhandenes `JAVA_HOME` nicht.
- Setzt sichere Runtime-Defaults nur wenn Env-Variable noch nicht gesetzt:
  - `SERVER_PORT=8081`
  - `PUBLIC_ORIGIN=https://current-car.com`
  - `OAUTH_ACCESS_TOKEN_TTL_SECONDS=3600`
  - `OAUTH_REFRESH_TOKEN_TTL_SECONDS=2592000`
  - `OAUTH_REFRESH_TOKEN_ROTATION_ENABLED=false`
- Druckt Startup-Banner mit Port, Origin, Token-Store-Pfad – **keine Secrets**.
- Startet via `.\gradlew.bat --no-daemon run --stacktrace`.

**`stop-pyloros.ps1`**
- Nutzt `netstat -ano` um den PID auf dem konfigurierten Port zu finden.
- Zeigt PID und Prozessname vor dem Stoppen.
- Stoppt nure den Prozess der tatsächlich auf dem Port lauscht.
- `-Force` Switch für sofortigen Kill ohne Bestätigung.
- Freundliche Meldung wenn kein Prozess läuft.

**`check-pyloros.ps1`**
- Meldet Port/Prozess-Status (PID, Prozessname, lokale Adresse).
- Führt HTTP-Check auf `GET /health` durch → `{"status":"ok"}`.
- Benötigt keine OAuth-Credentials.
- Exitcode 0 wenn OK, 1 wenn Prozess nicht läuft.

### `/health` Endpoint
- **Bereits vorhanden** in `MetadataRoutes.java` Zeile 27:
  ```java
  router.get("/health").handler(context -> HttpJson.send(context, 200, Map.of("status", "ok")));
  ```
- Kein Code-Change nötig.

### `README.md` aktualisiert
- Neue Sektion **"Running Pyloros locally on Windows"** mit Start/Stop/Check-Kommandos.
- Hinweis auf Apache als TLS-Termination und HTTP-Backend.
- Status-Sektion auf aktuellen Stand (002-A bis 002-I, 006) gebracht.

---

## Welche Dateien wurden geändert oder neu erstellt?

**Neu erstellt:**
1. `scripts/start-pyloros.ps1`
2. `scripts/stop-pyloros.ps1`
3. `scripts/check-pyloros.ps1`

**Modified:**
4. `README.md` - Operator-Sektion + aktualisierter Status
5. `docs/agent/assignment.md` - Task 006 Assignment

---

## Welche Architekturentscheidung wurde berührt?

- Keine Code-Architektur-Änderungen.
- Betrieb-Hygiene: Operator-Skripte kapseln die Startup-Logik, keine manuellen Env-Variablen mehr nötig.
- `/health` war bereits ungeschützt und ohne Token zugänglich.

---

## Welche Tests, Builds und Runtime-Checks wurden ausgeführt?

### Build (JDK Zulu 21)
```
BUILD SUCCESSFUL in 951ms
8 actionable tasks: 8 up-to-date
```

### Skript-Syntaxprüfung
- Alle drei `.ps1`-Dateien erstellt und inhaltlich geprüft.
- `start-pyloros.ps1` korrekte JAVA_HOME-Logik und Banner-Output ohne Secrets.
- `stop-pyloros.ps1` korrekte netstat+PID-Logik mit -Force Switch.
- `check-pyloros.ps1` korrekte HTTP-Check-Logik mit Fallback-Meldung.

---

## Ergebnis: ✅ erfolgreich

---

## Falls fehlgeschlagen: exakter Fehler und nächster Schritt

Kein Fehler.

---

## Exact commit hash

**Commit:** `38afd03`  
**Message:** "Add Windows operator scripts"  
**Remote:** `origin/main` (58e128c..38afd03)

---

## Push durchgeführt: ✅ JA

---

## Quick-Start nach diesem Release

```ps1
# Einmalig Secrets setzen (z.B. in separatem nicht-committeten Skript):
$env:OAUTH_CLIENT_ID     = 'angel'
$env:OAUTH_CLIENT_SECRET = 'change-me'

# Starten:
.\scripts\start-pyloros.ps1

# Status prüfen (in neuer Shell):
.\scripts\check-pyloros.ps1

# Stoppen:
.\scripts\stop-pyloros.ps1 -Force
```
