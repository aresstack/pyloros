# AGENTS.md

## Rolle und Arbeitsweise

Dieses Repository wird architektonisch bewusst geführt. Änderungen sollen klein, nachvollziehbar und buildbar bleiben.

Der zuständige Agent oder Junior-Programmierer arbeitet nicht als Produktentscheider. Architekturentscheidungen, Paketstruktur, Protokollverhalten und öffentliche Schnittstellen werden durch den PO/Architekten vorgegeben oder vor der Umsetzung abgestimmt.

## Projektgrundlagen

- Projektname: Pyloros
- Sprache: Java 21
- Framework: Vert.x
- Build: Gradle mit Groovy DSL
- Kein Spring
- Keine Kotlin-Gradle-Dateien
- Konfiguration vorerst über `application.properties` und Environment Variables

## Pflicht vor Implementierung

Vor größeren Änderungen immer prüfen:

1. Gibt es eine passende Anforderung unter `docs/requirements/`?
2. Ist die Entwicklungsumgebung korrekt? Insbesondere muss Gradle mit JDK 21 laufen.
3. Ist der aktuelle Stand buildbar oder ist ein bestehender Fehler dokumentiert?

Wenn eine Anforderung unklar ist, keine eigene Architektur erfinden. Stattdessen eine kurze Rückfrage oder eine Markdown-Notiz unter `docs/requirements/` erstellen.

## Pflicht nach jeder Implementierung

Nach jeder abgeschlossenen Implementierung muss eine klare Zusammenfassung geliefert werden.

Die Zusammenfassung muss diese Punkte enthalten:

```text
Zusammenfassung
- Was wurde umgesetzt?
- Welche Dateien wurden geändert oder neu angelegt?
- Welche Architekturentscheidung wurde berührt?
- Welche Tests/Builds wurden ausgeführt?
- Ergebnis: erfolgreich / fehlgeschlagen
- Falls fehlgeschlagen: konkrete Fehlermeldung und nächster empfohlener Schritt
```

Wenn kein Build oder Test ausgeführt werden konnte, muss das explizit genannt werden. Keine Formulierungen wie „sollte funktionieren“, ohne den fehlenden Nachweis zu markieren.

## Build- und Test-Regel

Nach Codeänderungen soll mindestens ausgeführt werden:

```powershell
.\gradlew.bat --no-daemon clean build --stacktrace
```

Wenn der Build wegen falscher Java-Version nicht startet, zuerst `docs/requirements/000-development-environment-java21.md` beachten.

## Architekturregeln

- `PylorosApplication` bleibt Bootstrap und Wiring.
- HTTP-Routen liegen unter `http/`.
- OAuth-Logik liegt nicht in den Routes, sondern im OAuth-Service.
- Tool-Aggregation läuft über `ToolProvider` und `ToolRegistry`.
- Neue Toolquellen werden als eigene Provider angebunden.
- Öffentliche Toolnamen sollen einen Prefix haben, z. B. `pyloros__`, `idea__`, `index__`.
- Pyloros muss starten können, auch wenn optionale Upstream-Server nicht erreichbar sind.

## Änderungsstil

- Kleine, reviewbare Schritte.
- Keine Big-Bang-Refactorings ohne Requirement.
- Keine geheimen Credentials in Git.
- Keine UI einführen, solange sie nicht explizit beauftragt wurde.
- Keine neuen Frameworks ohne Architekturfreigabe.

## Requirement-Dateien

Wenn eine Aufgabe nicht sofort sauber implementierbar ist, wird eine Markdown-Anforderung erstellt unter:

```text
docs/requirements/
```

Die Datei soll enthalten:

- Ziel
- Kontext
- gewünschte Pakete/Klassen
- Verhalten
- Fehlerverhalten
- Akzeptanzkriterien
- Nicht-Ziele

## Aktuelle nächste Anforderungen

- `docs/requirements/000-development-environment-java21.md`
- `docs/requirements/001-idea-mcp-upstream-provider.md`
