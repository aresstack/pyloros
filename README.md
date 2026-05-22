# Pyloros

Reactive Java / Vert.x gateway guarding and routing MCP tool access for AI-assisted development.


## Starten
```ps1
Set-Location 'C:\Projects\pyloros'

$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:SERVER_PORT = '64343'
$env:PUBLIC_ORIGIN = 'https://<deine url>'

$env:OAUTH_CLIENT_ID = '<dein user>'
$env:OAUTH_CLIENT_SECRET = '<dein aktuelles Connector Secret>'

.\gradlew.bat --no-daemon run --stacktrace
```
