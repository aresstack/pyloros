<#
.SYNOPSIS
    Check status of Pyloros MCP Gateway.

.DESCRIPTION
    Reports whether a process is listening on the Pyloros port and, if the
    /health endpoint is available, performs a lightweight HTTP check.
    No OAuth credentials are required.

.PARAMETER Port
    TCP port to check (default: 8081).

.EXAMPLE
    .\scripts\check-pyloros.ps1
    .\scripts\check-pyloros.ps1 -Port 8082
#>
[CmdletBinding()]
param(
    [int]$Port = 8081
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'SilentlyContinue'

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Pyloros Status Check  (port $Port)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# ── Port / process check ──────────────────────────────────────────────────────
$netLines = netstat -ano | Select-String "TCP\s+.*:$Port\s+.*LISTENING"

if (-not $netLines) {
    Write-Host "  Process    : NOT RUNNING (no listener on port $Port)" -ForegroundColor Red
    Write-Host ""
    exit 1
}

$pidStr = ($netLines[0] -split '\s+')[-1]
$pid_   = [int]$pidStr.Trim()
$proc   = Get-Process -Id $pid_ -ErrorAction SilentlyContinue
$procName = if ($proc) { $proc.Name } else { "(unknown)" }

Write-Host "  Process    : RUNNING" -ForegroundColor Green
Write-Host "  PID        : $pid_"
Write-Host "  Name       : $procName"
Write-Host "  Port       : $Port"

# ── HTTP health check ─────────────────────────────────────────────────────────
$healthUrl = "http://127.0.0.1:$Port/health"
Write-Host "  Health URL : $healthUrl"

try {
    $resp = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    if ($resp.StatusCode -eq 200) {
        Write-Host "  HTTP /health: OK ($($resp.StatusCode))  body=$($resp.Content.Trim())" -ForegroundColor Green
    } else {
        Write-Host "  HTTP /health: Unexpected status $($resp.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  HTTP /health: Unreachable or error — $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "               (Process is bound to port but HTTP is not yet responding.)"
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""
exit 0

