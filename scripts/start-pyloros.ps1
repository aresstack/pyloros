<#
.SYNOPSIS
    Start Pyloros MCP Gateway.

.DESCRIPTION
    Sets safe runtime defaults, resolves JAVA_HOME if needed, and launches
    Pyloros via the Gradle wrapper. Secrets are never printed to the console.

.EXAMPLE
    .\scripts\start-pyloros.ps1
    .\scripts\start-pyloros.ps1 -Port 8082
#>
[CmdletBinding()]
param(
    [int]$Port = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── Resolve project root ──────────────────────────────────────────────────────
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
Set-Location $ProjectRoot

# ── JAVA_HOME ─────────────────────────────────────────────────────────────────
if (-not $env:JAVA_HOME) {
    $zulu21 = 'C:\Program Files\Zulu\zulu-21'
    if (Test-Path $zulu21) {
        $env:JAVA_HOME = $zulu21
    }
}
if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

# ── Runtime defaults (only when not already set) ──────────────────────────────
if (-not $env:SERVER_PORT) {
    $env:SERVER_PORT = if ($Port -gt 0) { "$Port" } else { '8081' }
} elseif ($Port -gt 0) {
    $env:SERVER_PORT = "$Port"
}

if (-not $env:PUBLIC_ORIGIN)                        { $env:PUBLIC_ORIGIN                        = 'https://current-car.com' }
if (-not $env:OAUTH_ACCESS_TOKEN_TTL_SECONDS)       { $env:OAUTH_ACCESS_TOKEN_TTL_SECONDS        = '3600'     }
if (-not $env:OAUTH_REFRESH_TOKEN_TTL_SECONDS)      { $env:OAUTH_REFRESH_TOKEN_TTL_SECONDS       = '2592000'  }
if (-not $env:OAUTH_REFRESH_TOKEN_ROTATION_ENABLED) { $env:OAUTH_REFRESH_TOKEN_ROTATION_ENABLED  = 'false'    }

$storePath = if ($env:OAUTH_REFRESH_TOKEN_STORE_PATH) { $env:OAUTH_REFRESH_TOKEN_STORE_PATH } else { 'data/oauth-refresh-tokens.json' }

# ── Startup banner ─────────────────────────────────────────────────────────────
$javaVersion = (& java -version 2>&1 | Select-Object -First 1) -replace '\r?\n', ''
Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Pyloros MCP Gateway" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Project root : $ProjectRoot"
Write-Host "  Java         : $javaVersion"
Write-Host "  Server port  : $env:SERVER_PORT"
Write-Host "  Public origin: $env:PUBLIC_ORIGIN"
Write-Host "  Token store  : $storePath"
Write-Host "  Rotation     : $env:OAUTH_REFRESH_TOKEN_ROTATION_ENABLED"
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# ── Launch ────────────────────────────────────────────────────────────────────
& "$ProjectRoot\gradlew.bat" --no-daemon run --stacktrace

