# 000 - Development Environment: Java 21 for Pyloros

## Entscheidung

Pyloros wird als Java-21-Projekt gebaut. Gradle darf nicht mit Java 8 gestartet werden.

Der Junior-Programmierer soll vor weiteren Code-Aenderungen zuerst die lokale Build-Umgebung korrigieren.

## Grund

Das Projekt nutzt Java 21 und Vert.x 5. Auch wenn Vert.x 5 bereits ab Java 11 lauffaehig ist, ist Java 21 fuer Pyloros die gewaehlte Projektbasis.

Wenn Gradle mit Java 8 gestartet wird, bricht der Build bereits vor der Compilation ab. In diesem Zustand duerfen keine unvalidierten Code-Commits fuer Architektur- oder Provider-Arbeiten entstehen.

## Aufgabe

Stelle sicher, dass Gradle mit JDK 21 gestartet wird.

## Windows PowerShell Check

```powershell
java -version
$env:JAVA_HOME
```

Erwartung:

```text
Java 21.x
JAVA_HOME zeigt auf ein JDK 21
```

## Temporaer fuer eine PowerShell Session setzen

Passe den Pfad an die lokale Installation an:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21'
$env:Path = $env:JAVA_HOME + '\bin;' + $env:Path

Set-Location 'C:\Projects\pyloros'
.\gradlew.bat --no-daemon clean build --stacktrace
```

Alternative typische Pfade:

```text
C:\Program Files\Java\jdk-21
C:\Program Files\Eclipse Adoptium\jdk-21
C:\Program Files\Eclipse Adoptium\jdk-21.0.x.x-hotspot
```

## Dauerhafte Einstellung

Setze `JAVA_HOME` systemweit oder in der IntelliJ/Gradle-Konfiguration auf JDK 21.

In IntelliJ pruefen:

```text
Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JVM
```

Wert: JDK 21

## Akzeptanzkriterien

- `java -version` zeigt Java 21, wenn im Projekt-Terminal gearbeitet wird.
- `./gradlew.bat --no-daemon clean build --stacktrace` laeuft mindestens bis zur Java-Compilation.
- Wenn der Build danach wegen Codefehlern bricht, werden diese Codefehler separat berichtet.
- Keine weiteren Implementierungs-Commits fuer `001-idea-mcp-upstream-provider.md`, solange Gradle noch mit Java 8 startet.

## Danach

Nach erfolgreicher Umgebungskorrektur geht es weiter mit:

1. aktuellen Stand bauen
2. ToolProvider/ToolRegistry pruefen
3. `001-idea-mcp-upstream-provider.md` schrittweise umsetzen
